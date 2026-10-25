package com.qmurzik.tokskip.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.qmurzik.tokskip.core.Constants
import com.qmurzik.tokskip.core.TokSkip
import com.qmurzik.tokskip.core.data.VideoEntry
import com.qmurzik.tokskip.core.data.VideoKeys
import com.qmurzik.tokskip.core.model.Caption
import com.qmurzik.tokskip.core.model.Segment
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

/**
 * Rootless mode. Reads the current TikTok video from the accessibility tree, looks up / detects its
 * ad segments and skips them by setting the seek bar progress or dragging it with a gesture. When
 * that is impossible it shows a toast or swipes to the next video, as configured.
 */
class TokSkipAccessibilityService : AccessibilityService() {

    private lateinit var app: TokSkip
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var reader: ScreenReader
    private lateinit var overlay: MarkOverlay

    private val clock = PlaybackClock()
    private var videoKey: String? = null
    private var state: ScreenState? = null
    private var entry: VideoEntry? = null
    private val seenCaptions = linkedMapOf<String, Caption>()
    private val handledSegments = mutableSetOf<Segment>()
    private var handledLoop = -1L
    private var unknownHandled = false
    private var pendingStart: Long? = null
    private var tiktokInFront = false
    private var lastRead = 0L

    private val settings get() = app.settings.settings.value

    override fun onServiceConnected() {
        super.onServiceConnected()
        app = TokSkip.get(this)
        reader = ScreenReader { settings }
        overlay = MarkOverlay(this, ::markStart, ::markEnd) { requestDump(0) }
        instance = this
        log("служба доступности подключена")
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

        val key = VideoKeys.uiKey(s.author, s.description) ?: return
        if (key != videoKey) onNewVideo(key, s)
        val pos = clock.position()
        for (text in s.captions) if (text !in seenCaptions) seenCaptions[text] = Caption(pos, pos + 2_000, text)
    }

    private fun onNewVideo(key: String, s: ScreenState) {
        finishVideo()
        videoKey = key
        entry = null
        clock.reset()
        s.durationMs?.let { clock.durationMs = it }
        handledSegments.clear(); handledLoop = -1; unknownHandled = false; pendingStart = null
        if (settings.verboseLog) log("ролик $key @${s.author}: ${s.description.take(60)}")
        scope.launch {
            val stored = app.find(listOf(key))
            if (key != videoKey) return@launch
            entry = stored ?: detectFromText(key, s)
            entry?.let { log("ролик @${s.author}: ${describe(it)}") }
        }
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

    /** When leaving a video, detect over the captions that were seen while it played. */
    private fun finishVideo() {
        val key = videoKey ?: return
        val s = state ?: return
        if (!settings.autoDetect || seenCaptions.size < 2 || entry?.source == com.qmurzik.tokskip.core.model.SegmentSource.MANUAL) {
            seenCaptions.clear(); return
        }
        val meta = VideoMeta(key, s.author, s.description, captions = seenCaptions.values.toList(), durationMs = clock.durationMs)
        seenCaptions.clear()
        scope.launch {
            val result = app.detector().detect(meta)
            if (result.segments.isNotEmpty()) {
                app.saveAuto(meta, result)
                log("по субтитрам найдено: ${result.segments.joinToString { fmt(it) }}; сохранено для повторного просмотра")
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            runCatching { if (tiktokInFront && settings.enabled) check() }.onFailure { log("tick: $it") }
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
        overlay.status(fmtMs(pos) + if (pendingStart != null) " (метка ${fmtMs(pendingStart!!)})" else "", pendingStart != null)
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
            fallback(settings.fallbackAction, "Реклама до ${fmtMs(seg.endMs)}")
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
        if (videoKey == null) { toast("Ролик не распознан — откройте TikTok"); return }
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

        fun fmtMs(ms: Long) = "%d:%02d.%d".format(ms / 60_000, ms / 1000 % 60, ms / 100 % 10)
        fun fmt(s: Segment) = "${fmtMs(s.startMs)}–${fmtMs(s.endMs)}"
    }
}
