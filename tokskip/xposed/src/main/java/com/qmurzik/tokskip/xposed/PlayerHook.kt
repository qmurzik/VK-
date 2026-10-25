package com.qmurzik.tokskip.xposed

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.qmurzik.tokskip.core.model.Segment
import com.qmurzik.tokskip.core.settings.FallbackAction
import com.qmurzik.tokskip.core.settings.Mode
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * Watches TikTok's video engine and seeks over ad segments.
 *
 * TikTok plays through ByteDance's TTVideoEngine SDK, whose public API keeps its names
 * (getCurrentPlaybackTime / getDuration / seekTo / setVideoID / play). If the class is renamed,
 * DexKit locates it as the class declaring `getCurrentPlaybackTime()`. The engine's data source
 * (video id / URL) maps it to the feed item captured by [FeedHook].
 */
object PlayerHook {
    private val sources = Collections.synchronizedMap(WeakHashMap<Any, String>())
    @Volatile private var current: WeakReference<Any>? = null
    private lateinit var position: Method
    private var seek: Method? = null
    private val main = Handler(Looper.getMainLooper())
    private var context: Context? = null

    private var handledAid: String? = null
    private val handled = mutableSetOf<Segment>()
    private var unknownShown = false
    private var lastPos = 0L

    fun currentAid(): String? = current?.get()?.let { VideoStore.aidForSource(sources[it]) }

    fun install(finder: Finder, ctx: () -> Context?): Boolean {
        val engine = finder.findClass("engine", ENGINE_NAMES, "getCurrentPlaybackTime", "int") ?: run {
            L.w("плеер: движок не найден — перемотка в root-режиме отключена"); return false
        }
        position = zeroArg(engine, POSITION_NAMES) ?: run { L.w("плеер: нет метода позиции в ${engine.name}"); return false }
        seek = engine.methods.firstOrNull { it.name == "seekTo" && it.parameterTypes.firstOrNull() in INTS }
        if (seek == null) L.w("плеер: seekTo не найден — будет только уведомление о рекламе")

        var hooks = 0
        for (m in engine.methods) {
            if (m.declaringClass == Any::class.java) continue
            val p = m.parameterTypes
            when {
                m.name in SOURCE_SETTERS && p.isNotEmpty() && p[0] == String::class.java ->
                    Hooks.before(m) { c -> (c.args.firstOrNull() as? String)?.let { sources[c.thisObject] = it } }?.let { hooks++ }
                m.name in PLAY_METHODS && p.isEmpty() ->
                    Hooks.before(m) { c -> c.thisObject?.let { current = WeakReference(it) } }?.let { hooks++ }
            }
        }
        if (hooks == 0) { L.w("плеер: не удалось перехватить ${engine.name}"); return false }
        context = ctx()
        L.i("плеер: ${engine.name}, хуков $hooks, seekTo=${seek != null}")
        main.post(tick)
        return true
    }

    private val tick = object : Runnable {
        override fun run() {
            runCatching { check() }.onFailure { L.w("плеер: tick", it) }
            main.postDelayed(this, 200)
        }
    }

    private fun check() {
        val s = Bridge.settings
        // Without settings (provider unreachable) the active LSPosed module itself means "root mode".
        if (s != null && (!s.enabled || s.mode != Mode.ROOT)) return
        val engine = current?.get() ?: return
        val aid = VideoStore.aidForSource(sources[engine]) ?: return
        val pos = (position.invoke(engine) as Number).toLong()
        if (aid != handledAid || pos + 1_500 < lastPos) {
            if (aid != handledAid) unknownShown = false
            handledAid = aid; handled.clear()   // new video or a loop restart
        }
        lastPos = pos
        val known = VideoStore.known(aid) ?: return
        if (known.segments.isEmpty()) {
            if (known.hasAd && !unknownShown) {
                unknownShown = true
                if ((s?.unknownSegmentAction ?: FallbackAction.NOTHING) != FallbackAction.NOTHING) toast("Ролик содержит рекламу")
            }
            return
        }
        if (s?.skipSegments == false) return
        val seg = known.segments.firstOrNull { pos in it && it !in handled } ?: return
        handled += seg
        if (seekTo(engine, seg.endMs)) {
            lastPos = seg.endMs
            Bridge.skipped(seg.endMs - pos)
            L.i("перемотка $aid: $pos → ${seg.endMs}")
        } else if ((s?.fallbackAction ?: FallbackAction.TOAST) != FallbackAction.NOTHING) {
            toast("Реклама до ${seg.endMs / 1000} с")
        }
    }

    private fun seekTo(engine: Any, ms: Long): Boolean {
        val m = seek ?: return false
        val args: List<Any?> = m.parameterTypes.mapIndexed { i, t ->
            when {
                i == 0 && (t == Int::class.javaPrimitiveType || t == Int::class.javaObjectType) -> ms.toInt()
                i == 0 -> ms
                t == Boolean::class.javaPrimitiveType -> false
                t.isPrimitive -> 0
                else -> null
            }
        }
        return runCatching { m.invoke(engine, *args.toTypedArray<Any?>()); true }.onFailure { L.w("плеер: seekTo", it) }.getOrDefault(false)
    }

    private fun toast(msg: String) {
        val ctx = context ?: return
        runCatching { Toast.makeText(ctx, "TokSkip: $msg", Toast.LENGTH_SHORT).show() }
    }

    fun attachContext(ctx: Context) { context = ctx }

    private fun zeroArg(cls: Class<*>, names: List<String>): Method? = names.firstNotNullOfOrNull { n ->
        cls.methods.firstOrNull { it.name == n && it.parameterTypes.isEmpty() && (it.returnType in INTS) }
    }

    private val INTS = setOf(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType, Int::class.javaObjectType, Long::class.javaObjectType)
    private val ENGINE_NAMES = listOf("com.ss.ttvideoengine.TTVideoEngine")
    private val POSITION_NAMES = listOf("getCurrentPlaybackTime", "getCurrentPosition", "getCurrentPlayTime")
    private val SOURCE_SETTERS = setOf("setVideoID", "setVideoId", "setVid", "setDirectURL", "setDirectUrlUseDataLoader", "setLocalURL", "setDataSource")
    private val PLAY_METHODS = setOf("play", "start", "resume")
}
