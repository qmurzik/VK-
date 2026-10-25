package com.qmurzik.tokskip.xposed

import com.qmurzik.tokskip.core.detect.CaptionParser

/**
 * Picks up subtitle text (TikTok's auto-captions are WebVTT) where the app parses it. The parsers
 * are found by DexKit as methods using the "WEBVTT" literal; any String argument or result that
 * looks like subtitles is parsed and attached to the video that is playing.
 */
object CaptionHook {
    fun install(finder: Finder): Boolean {
        val methods = finder.findMethodsUsing("vtt", "WEBVTT")
        if (methods.isEmpty()) { L.w("субтитры: парсер WebVTT не найден — анализ субтитров отключён"); return false }
        var ok = 0
        for (m in methods) {
            val handle = Hooks.intercept(m) { chain ->
                runCatching { chain.args.forEach { inspect(it) } }
                val r = chain.proceed()
                runCatching { inspect(r) }
                r
            }
            if (handle != null) ok++
        }
        L.i("субтитры: хуков $ok из ${methods.size} (${methods.joinToString { it.declaringClass.simpleName + "." + it.name }})")
        return ok > 0
    }

    private var lastText: String? = null

    private fun inspect(value: Any?) {
        val text = value as? String ?: return
        if (text.length < 20 || text === lastText || !CaptionParser.looksLikeSubtitles(text)) return
        lastText = text
        val aid = PlayerHook.currentAid() ?: run { L.d { "субтитры без активного ролика" }; return }
        val captions = CaptionParser.parse(text)
        L.d { "субтитры для $aid: ${captions.size} строк" }
        if (captions.isNotEmpty()) VideoStore.onCaptions(aid, captions)
    }
}
