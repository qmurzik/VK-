package com.qmurzik.tokskip.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.qmurzik.tokskip.core.Constants
import com.qmurzik.tokskip.core.TokSkip
import com.qmurzik.tokskip.core.data.VideoEntry
import com.qmurzik.tokskip.core.data.VideoKeys
import com.qmurzik.tokskip.core.model.Segment
import com.qmurzik.tokskip.core.model.SegmentSource
import com.qmurzik.tokskip.core.model.VideoMeta
import com.qmurzik.tokskip.core.settings.FallbackAction
import com.qmurzik.tokskip.core.settings.Mode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/**
 * Rootless mode.
 *
 * - Reads the current TikTok video from the accessibility tree and looks up its stored segments.
 * - Once a second takes a screenshot (Android 11+), blanks TikTok's UI and OCRs what is left —
 *   text inside the video: burned-in subtitles, ad banners. That text, stamped with the playback
 *   position, goes to the detector. When an ad is on screen *now*, the service seeks forward in
 *   steps until it is gone, then stores the segment so the next viewing skips it at once.
 * - Seeking uses ACTION_SET_PROGRESS on TikTok's seek bar, else a drag gesture along it; without
 *   a seek bar it falls back to a toast or a swipe to the next video, as configured.
 */
class TokSkipAccessibilityService : AccessibilityService() {

    private lateinit var app: TokSkip
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val ocrThread = Executors.newSingleThreadExecutor { Thread(it, "TokSkip-ocr").apply { priority = Thread.MIN_PRIORITY } }
    private lateinit var reader: ScreenReader
    private lateinit var overlay: MarkOverlay
    private lateinit var ocr: ScreenOcr

    private val clock = PlaybackClock()
    private var videoKey: String? = null
    /** False for an `anon:` key: the video could not be identified from its text. */
    private var keyIsStable = false
    private var state: ScreenState? = null
    private var entry: VideoEntry? = null
    private val live = LiveScreenText()
    private var seenCaptions = HashSet<String>()
    private val handledSegments = mutableSetOf<Segment>()
    private var handledLoop = -1L
    private var unknownHandled = false
    private var pendingStart: Long? = null
    private var tiktokInFront = false
    private var lastRead = 0L
    private var scrollEpoch = 0
    private var ocrBusy = false
    private var lastShot = 0L
    private var liveAd = false
    private var liveFallbackDone = false
    private var liveSkippedMs = 0L

    private val settings get() = app.settings.settings.value

    override fun onServiceConnected() {
        super.onServiceConnected()
        app = TokSkip.get(this)
        reader = ScreenReader { settings }
        ocr = ScreenOcr(this)
        overlay = MarkOverlay(this, ::markStart, ::markEnd) { requestDump(0) }
        instance = this
        log("служба доступности подключена; анализ экрана: " +
            if (Build.VERSION.SDK_INT >= 30) (if (settings.screenOcr) "включён" else "выключен в настройках") else "нужен Android 11+")
        scope.launch {
            app.settings.settings.distinctUntilChangedBy { it.showOverlay }.collect { updateOverlay() }
        }
        handler.post(tick)
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        overlay.hide()
        scope.cancel()
        ocrThread.execute { ocr.close() }
        ocrThread.shutdown()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val front = pkg in Constants.TIKTOK_PACKAGES
            if (front != tiktokInFront) { tiktokInFront = front; updateOverlay() }
        }
        if (pkg !in Constants.TIKTOK_PACKAGES || !settings.enabled) return
        tiktokInFront = true
        // A vertical scroll of the feed is the only video-change signal when the text is unreadable.
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && Build.VERSION.SDK_INT >= 28 && event.scrollDeltaY != 0) scrollEpoch++
        // Content events arrive in bursts; read the tree at most every 300 ms.
        val now = System.currentTimeMillis()
        if (now - lastRead < 300) return
        lastRead = now
        refresh()
    }

    /** Re-reads the screen and switches the current video when its text changes. */
    private fun refresh() {
        val root = tiktokRoot() ?: return
        val s = runCatching { reader.read(root) }.onFailure { log("ошибка чтения экрана: $it") }.getOrNull() ?: return
        state = s
        s.durationMs?.let { clock.durationMs = it }
        s.positionMs?.let { clock.observeIfChanged("label", it) }
        seekbarPosition(s)?.let { clock.observeIfChanged("bar", it) }

        val stable = VideoKeys.uiKey(s.author, s.description)
        val key = stable ?: "anon:$scrollEpoch"
        if (key != videoKey) onNewVideo(key, stable != null, s)
        val fresh = s.captions.filter { seenCaptions.add(it) }
        if (fresh.isNotEmpty()) live.add(clock.position(), fresh, 2_000)
    }

    private fun onNewVideo(key: String, stable: Boolean, s: ScreenState) {
        finishVideo()
        videoKey = key
        keyIsStable = stable
        entry = null
        clock.reset()
        s.durationMs?.let { clock.durationMs = it }
        handledSegments.clear(); handledLoop = -1; unknownHandled = false; pendingStart = null
        live.reset(); seenCaptions = HashSet(); liveAd = false; liveFallbackDone = false; liveSkippedMs = 0
        log("ролик ${if (stable) "@${s.author.ifEmpty { "?" }}" else "(не опознан по тексту)"} · " + diagnostics(s))
        if (!stable) return
        scope.launch {
            val stored = app.find(listOf(key))
            if (key != videoKey) return@launch
            entry = stored ?: detectFromText(key, s)
            entry?.let { log("  в базе: ${describe(it)}") }
        }
    }

    /** What the service can see — the first thing to check when skipping does not work. */
    private fun diagnostics(s: ScreenState): String {
        val bar = s.seekbar?.let { "полоса есть (${if (it.canSetProgress) "setProgress" else "только жест"}, max=${it.max.toInt()})" }
            ?: "полосы прогресса нет — перемотка невозможна, сработает резервное действие"
        val dur = s.durationMs?.let { "длительность ${fmtMs(it)}" } ?: "длительность неизвестна"
        return "описание: ${if (s.description.isEmpty()) "не найдено" else "${s.description.length} симв."} · $bar · $dur"
    }

    /** Runs the detector on the description when nothing is stored for this video. */
    private suspend fun detectFromText(key: String, s: ScreenState): VideoEntry? {
        if (!settings.autoDetect) return null
        val meta = VideoMeta(key, s.author, s.description, onScreenText = s.captions)
        val result = app.detector().detect(meta)
        if (!result.hasAd) return null
        app.saveAuto(meta, result)
        return app.find(listOf(key))
    }

    private fun baseMeta(): VideoMeta? {
        val key = videoKey ?: return null
        val s = state ?: return null
        return VideoMeta(key, s.author, s.description, durationMs = clock.durationMs)
    }

    /** When leaving a video, store what the screen text revealed (for replays and export). */
    private fun finishVideo() {
        val meta = baseMeta() ?: return
        if (!keyIsStable || !settings.autoDetect || live.size < 2 || entry?.source == SegmentSource.MANUAL) return
        val result = live.detect(app.detector(), meta)
        if (result.segments.isEmpty()) return
        scope.launch {
            app.saveAuto(meta, result)
            log("  по тексту на экране: ${result.segments.joinToString { fmt(it) }} — сохранено")
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            runCatching {
                if (tiktokInFront && settings.enabled) { check(); maybeCapture() }
            }.onFailure { log("tick: $it") }
            handler.postDelayed(this, 250)
        }
    }

    private fun check() {
        state?.let { st ->
            val sb = st.seekbar ?: return@let
            if (!sb.node.refresh()) return@let
            val updated = st.copy(seekbar = readBar(sb))
            state = updated
            seekbarPosition(updated)?.let { clock.observeIfChanged("bar", it) }
        }
        val pos = clock.position()
        val bar = if (state?.seekbar != null) "полоса ✓" else "полосы нет"
        val ad = if (liveAd) " · РЕКЛАМА" else ""
        overlay.status(fmtMs(pos) + " · $bar$ad" + if (pendingStart != null) " (метка ${fmtMs(pendingStart!!)})" else "", pendingStart != null)
        val e = entry ?: return
        // In root mode the LSPosed hook seeks with exact positions; this service only reads and marks.
        if (settings.mode == Mode.ROOT) return
        if (clock.loops() != handledLoop) { handledLoop = clock.loops(); handledSegments.clear() }

        if (e.segments.isEmpty()) {
            if (e.hasAd && !unknownHandled) { unknownHandled = true; fallback(settings.unknownSegmentAction, "Ролик содержит рекламу") }
            return
        }
        if (!settings.skipSegments) return
        val seg = e.segments.firstOrNull { pos in it && it !in handledSegments } ?: return
        handledSegments += seg
        if (seekTo(seg.endMs)) {
            clock.observe(seg.endMs)
            app.settings.addSkip(seg.endMs - pos)
            log("перемотка ${fmtMs(pos)} → ${fmtMs(seg.endMs)}")
        } else {
            log("перемотать нельзя (${if (state?.seekbar == null) "нет полосы прогресса" else "TikTok не принял жест"})")
            fallback(settings.fallbackAction, "Реклама до ${fmtMs(seg.endMs)}")
        }
    }

    // ---- screen OCR ----

    private fun maybeCapture() {
        if (Build.VERSION.SDK_INT < 30 || !settings.screenOcr || !settings.autoDetect || !ocr.available) return
        val key = videoKey ?: return
        val now = System.currentTimeMillis()
        if (ocrBusy || now - lastShot < OCR_INTERVAL_MS) return
        ocrBusy = true
        lastShot = now
        val pos = clock.position()
        val masks = state?.uiBounds.orEmpty()
        takeScreenshot(Display.DEFAULT_DISPLAY, ocrThread, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val lines = runCatching {
                    result.hardwareBuffer.use { hb ->
                        val shot = Bitmap.wrapHardwareBuffer(hb, result.colorSpace) ?: return@use null
                        val frame = ScreenOcr.prepare(shot, masks)
                        shot.recycle()
                        ocr.recognize(frame).also { frame.recycle() }
                    }
                }.getOrNull()
                handler.post { onOcr(key, pos, lines) }
            }

            override fun onFailure(errorCode: Int) {
                handler.post { ocrBusy = false }
            }
        })
    }

    private fun onOcr(key: String, pos: Long, lines: List<String>?) {
        ocrBusy = false
        if (lines == null) { if (!ocr.available) log("OCR недоступен на этом устройстве"); return }
        if (key != videoKey) return
        live.add(pos, lines, OCR_INTERVAL_MS)
        if (settings.verboseLog && lines.isNotEmpty()) log("  OCR ${fmtMs(pos)}: ${lines.joinToString(" | ").take(300)}")
        val meta = baseMeta() ?: return
        if (entry?.source == SegmentSource.MANUAL) return
        val result = live.detect(app.detector(), meta)
        val now = result.segments.firstOrNull { pos >= it.startMs && pos <= it.endMs }
        if (now != null) onLiveAd(pos, now) else if (liveAd) onLiveAdEnded(meta, result)
    }

    /** An ad is on screen right now; its end is unknown, so seek forward in steps while it is visible. */
    private fun onLiveAd(pos: Long, seg: Segment) {
        if (!liveAd) log("реклама на экране с ${fmtMs(seg.startMs)} (уверенность ${(seg.confidence * 100).toInt()}%)")
        liveAd = true
        if (settings.mode == Mode.ROOT || !settings.skipSegments) return
        val dur = clock.durationMs
        val target = (pos + LIVE_STEP_MS).let { if (dur > 0) it.coerceAtMost(dur - 500) else it }
        if (target > pos && seekTo(target)) {
            clock.observe(target)
            app.settings.addSkip(target - pos, newVideo = liveSkippedMs == 0L)
            liveSkippedMs += target - pos
            log("  перемотка ${fmtMs(pos)} → ${fmtMs(target)} (реклама ещё видна — шаг ${LIVE_STEP_MS / 1000} с)")
        } else if (!liveFallbackDone) {
            liveFallbackDone = true
            log("  перемотать нельзя (${if (state?.seekbar == null) "нет полосы прогресса" else "TikTok не принял жест"})")
            fallback(settings.fallbackAction, "Реклама")
        }
    }

    private fun onLiveAdEnded(meta: VideoMeta, result: com.qmurzik.tokskip.core.model.DetectionResult) {
        liveAd = false
        liveFallbackDone = false
        log("реклама закончилась: ${result.segments.joinToString { fmt(it) }}")
        if (!keyIsStable) return
        scope.launch {
            app.saveAuto(meta, result)
            entry = app.find(listOf(meta.videoId))
            entry?.segments?.let { handledSegments.addAll(it) } // already skipped live in this loop
        }
    }

    private fun readBar(sb: SeekbarInfo): SeekbarInfo {
        val r = sb.node.rangeInfo ?: return sb
        return SeekbarInfo(sb.node, sb.bounds, r.current, r.max, sb.canSetProgress)
    }

    /** Converts the seek bar progress to ms when its scale is known. */
    private fun seekbarPosition(s: ScreenState): Long? {
        val sb = s.seekbar ?: return null
        if (sb.max <= 0 || sb.current < 0) return null
        val dur = clock.durationMs.takeIf { it > 0 }
        return when {
            dur != null -> (sb.current / sb.max * dur).toLong()
            sb.max > 1000 -> { clock.durationMs = sb.max.toLong(); sb.current.toLong() } // scale looks like ms
            else -> null
        }
    }

    /** Seeks via ACTION_SET_PROGRESS, else by dragging the seek bar. */
    private fun seekTo(targetMs: Long): Boolean {
        val sb = state?.seekbar ?: return false
        val dur = clock.durationMs.takeIf { it > 0 } ?: if (sb.max > 1000) sb.max.toLong() else return false
        val fraction = (targetMs.toFloat() / dur).coerceIn(0f, 1f)
        if (sb.canSetProgress && sb.max > 0) {
            val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, fraction * sb.max) }
            if (sb.node.performAction(android.R.id.accessibilityActionSetProgress, args)) return true
        }
        val b = sb.bounds
        val y = b.exactCenterY()
        val fromX = b.left + b.width() * (clock.position().toFloat() / dur).coerceIn(0f, 1f)
        val toX = b.left + b.width() * fraction
        val path = Path().apply { moveTo(fromX, y); lineTo(toX, y) }
        return dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 350)).build(), null, null)
    }

    private fun fallback(action: FallbackAction, message: String) {
        when (action) {
            FallbackAction.NOTHING -> Unit
            FallbackAction.TOAST -> Toast.makeText(this, "TokSkip: $message", Toast.LENGTH_SHORT).show()
            FallbackAction.SWIPE -> {
                val screen = state?.screen ?: return
                val x = screen.exactCenterX()
                val path = Path().apply { moveTo(x, screen.height() * 0.75f); lineTo(x, screen.height() * 0.25f) }
                dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 220)).build(), null, null)
                app.settings.addSkip(0)
                log("перелистнул ролик: $message")
            }
        }
    }

    // ---- manual markup ----

    private fun markStart() {
        if (videoKey == null || !keyIsStable) { toast("Ролик не опознан по описанию — разметка некуда сохранить. Сделайте «Дамп UI» и пришлите его."); return }
        pendingStart = clock.position()
        toast("Начало рекламы: ${fmtMs(pendingStart!!)}")
    }

    private fun markEnd() {
        val key = videoKey ?: return
        val start = pendingStart ?: run { toast("Сначала отметьте начало"); return }
        val end = clock.position()
        if (end <= start) { toast("Конец раньше начала"); return }
        pendingStart = null
        val s = state
        val seg = Segment(start, end, 1f)
        scope.launch {
            app.addManualSegment(key, s?.author.orEmpty(), s?.description.orEmpty(), seg)
            entry = app.find(listOf(key))
            handledSegments += seg
            toast("Сохранено: ${fmt(seg)}")
            log("ручная разметка $key: ${fmt(seg)}")
        }
    }

    // ---- UI dump ----

    /** Dumps the TikTok window (or the active one) after [delayMs], so the user can switch apps. */
    fun requestDump(delayMs: Long) {
        handler.postDelayed({
            val root = tiktokRoot() ?: rootInActiveWindow
            if (root == null) { toast("Нет доступного окна для дампа"); return@postDelayed }
            val file: File? = runCatching { UiDumper.dump(this, root) }.onFailure { log("дамп не удался: $it") }.getOrNull()
            if (file != null) { toast("Дамп сохранён: ${file.name}"); log("дамп UI: ${file.absolutePath}") }
        }, delayMs)
    }

    private fun tiktokRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.takeIf { it.packageName?.toString() in Constants.TIKTOK_PACKAGES }?.let { return it }
        return runCatching { windows }.getOrNull().orEmpty().firstNotNullOfOrNull { w ->
            w.root?.takeIf { it.packageName?.toString() in Constants.TIKTOK_PACKAGES }
        }
    }

    private fun updateOverlay() {
        if (settings.showOverlay && tiktokInFront) overlay.show() else overlay.hide()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun log(msg: String) = app.log.log("a11y", msg)

    private fun describe(e: VideoEntry) =
        if (e.segments.isEmpty()) "реклама без таймкодов (${e.source})" else e.segments.joinToString(prefix = "${e.source}: ") { fmt(it) }

    companion object {
        /** The running service, for the "UI dump" screen. Null when the service is off. */
        @Volatile var instance: TokSkipAccessibilityService? = null
            private set

        private const val OCR_INTERVAL_MS = 1_000L
        private const val LIVE_STEP_MS = 4_000L

        fun fmtMs(ms: Long) = "%d:%02d.%d".format(ms / 60_000, ms / 1000 % 60, ms / 100 % 10)
        fun fmt(s: Segment) = "${fmtMs(s.startMs)}–${fmtMs(s.endMs)}"
    }
}
