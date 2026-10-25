package com.qmurzik.tokskip.xposed

import com.qmurzik.tokskip.core.model.VideoMeta
import java.util.Collections
import java.util.WeakHashMap

/**
 * Captures every video model ("Aweme") the app touches — feed parsing, preloading, binding — so the
 * detector runs before the video is shown.
 *
 * Instead of hooking the (obfuscated, protobuf/JSON, often-changing) feed parser, the hook sits on
 * the model's id getter, which the feed code calls for every item right after parsing. The model
 * class is a kept Gson/serializable bean, found by its historical name or, failing that, by DexKit
 * as the class declaring `getAid(): String`.
 */
object FeedHook {
    private val seen = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    @Volatile private var described = false

    fun install(finder: Finder): Boolean {
        val aweme = finder.findClass("aweme", AWEME_NAMES, "getAid", "java.lang.String") ?: run {
            L.w("лента: класс Aweme не найден — предварительный анализ отключён"); return false
        }
        val getAid = runCatching { aweme.getDeclaredMethod("getAid") }.getOrNull() ?: run {
            L.w("лента: ${aweme.name}.getAid отсутствует"); return false
        }
        Hooks.after(getAid) { chain, result ->
            val obj = chain.thisObject ?: return@after
            if (result !is String || result.isEmpty() || seen.put(obj, true) != null) return@after
            onAweme(obj, result)
        } ?: return false
        L.i("лента: хук на ${aweme.name}.getAid")
        return true
    }

    private fun onAweme(aweme: Any, aid: String) {
        if (!described) {
            described = true
            L.d { "Aweme getters: " + Beans.describe(aweme.javaClass) }
            Beans.get(aweme, "getVideo", "video")?.let { v -> L.d { "Video getters: " + Beans.describe(v.javaClass) } }
        }
        val author = Beans.get(aweme, "getAuthor", "author")
        val video = Beans.get(aweme, "getVideo", "video")
        val playAddr = Beans.get(video, "getPlayAddr", "play_addr")
        val meta = VideoMeta(
            videoId = aid,
            author = Beans.string(author, "getUniqueId", "unique_id", "getNickname", "nickname").orEmpty(),
            description = Beans.string(aweme, "getDesc", "desc").orEmpty(),
            durationMs = Beans.long(video, "getDuration", "duration") ?: Beans.long(aweme, "getDuration", "duration") ?: 0,
        )
        val sources = listOfNotNull(
            Beans.string(playAddr, "getUri", "uri"),
            Beans.string(playAddr, "getUrlKey", "url_key"),
            Beans.string(video, "getVideoId", "getVid", "vid"),
            Beans.string(playAddr, "getFileHash", "file_hash"),
        )
        VideoStore.onFeedItem(meta, sources)
    }

    private val AWEME_NAMES = listOf("com.ss.android.ugc.aweme.feed.model.Aweme")
}
