package com.qmurzik.vkenhancer.hook.net

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/**
 * What to cut out of VK API responses.
 *
 * Every rule is expressed in terms of the public VK API (item `type` values, `marked_as_ads`,
 * `copy_history`, ...). Those names are part of the server contract and do not change between app
 * builds, which is what makes response filtering independent of the app version.
 */
data class FilterRules(
    /** Exact `type` values removed from feed-like responses. */
    val feedTypes: Set<String> = emptySet(),
    /** `type` prefixes removed from feed-like responses (for example `clips_`). */
    val feedTypePrefixes: List<String> = emptyList(),
    /** Remove items whose `type` starts with `ads_` everywhere they appear. */
    val adTypes: Boolean = false,
    /** Remove posts flagged by the API as advertising (`marked_as_ads`, `ad_data`). */
    val markedAds: Boolean = false,
    /** Remove reposts (posts carrying `copy_history`) from feeds. */
    val reposts: Boolean = false,
    /** Lower-case words; feed posts containing any of them are removed. */
    val keywords: List<String> = emptyList(),
    /** Drop advertising settings attached to stories responses. */
    val storyAds: Boolean = false,
    /** Return empty story lists. */
    val disableStories: Boolean = false,
    /** Drop promo conversations injected into the chat list. */
    val promoChats: Boolean = false,
    /** Drop pre-roll advertising descriptors attached to videos. */
    val videoAds: Boolean = false,
) {
    val isEmpty: Boolean
        get() = feedTypes.isEmpty() && feedTypePrefixes.isEmpty() && !adTypes && !markedAds && !reposts &&
            keywords.isEmpty() && !storyAds && !disableStories && !promoChats && !videoAds
}

object ApiFilter {
    private const val MAX_DEPTH = 12

    private val STORY_AD_KEYS = setOf("ads_settings", "ads", "ads_block", "promo_data", "ad_config")
    private val VIDEO_AD_KEYS = setOf("ads_info", "ads")

    /** Extracts `newsfeed.getFeed` from `https://api.vk.ru/method/newsfeed.getFeed?v=5.x`. */
    fun methodFromUrl(url: String): String? {
        val idx = url.indexOf("/method/")
        if (idx < 0) return null
        val start = idx + "/method/".length
        var end = start
        while (end < url.length) {
            val c = url[end]
            if (c == '?' || c == '/' || c == '#' || c == '&') break
            end++
        }
        return if (end > start) url.substring(start, end) else null
    }

    /**
     * Filters one API response body.
     *
     * @return the rewritten body, or null when nothing was removed (the caller keeps the original
     * bytes untouched in that case).
     */
    fun filter(method: String, body: String, rules: FilterRules): String? {
        if (rules.isEmpty) return null
        val trimmedStart = body.indexOfFirst { !it.isWhitespace() }
        if (trimmedStart < 0 || body[trimmedStart] != '{') return null
        if (!mightMatch(method, body, rules)) return null

        val root = try {
            JSONTokener(body).nextValue() as? JSONObject ?: return null
        } catch (e: Exception) {
            return null
        }
        val ctx = Context(method.lowercase(Locale.ROOT), rules)
        val response = root.opt("response") ?: return null
        walk(response, ctx, 0)
        return if (ctx.changed) root.toString() else null
    }

    /** Cheap substring checks so that most responses are never parsed. */
    private fun mightMatch(method: String, body: String, rules: FilterRules): Boolean {
        val m = method.lowercase(Locale.ROOT)
        if (rules.disableStories && m.startsWith("stories.")) return true
        if (isFeedMethod(m) && (rules.feedTypes.isNotEmpty() || rules.feedTypePrefixes.isNotEmpty() ||
                rules.reposts || rules.keywords.isNotEmpty())
        ) return true
        if (rules.adTypes && body.contains("\"ads_")) return true
        if (rules.markedAds && (body.contains("\"marked_as_ads\"") || body.contains("\"ad_data\""))) return true
        if (rules.storyAds && (m.startsWith("stories.") || m.startsWith("execute")) && body.contains("\"ads")) return true
        if (rules.promoChats && body.contains("\"is_promo\"")) return true
        if (rules.videoAds && body.contains("\"ads")) return true
        return false
    }

    private fun isFeedMethod(m: String) =
        m.startsWith("newsfeed.") || m.startsWith("execute") || m.startsWith("wall.get") || m.contains("feed")

    private class Context(val method: String, val rules: FilterRules) {
        var changed = false
        val feed = isFeedMethod(method)
        val stories = method.startsWith("stories.") || method.startsWith("execute")
    }

    private fun walk(node: Any?, ctx: Context, depth: Int) {
        if (depth > MAX_DEPTH) return
        when (node) {
            is JSONObject -> walkObject(node, ctx, depth)
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), ctx, depth + 1)
        }
    }

    private fun walkObject(obj: JSONObject, ctx: Context, depth: Int) {
        val rules = ctx.rules
        if (rules.storyAds && ctx.stories) removeKeys(obj, STORY_AD_KEYS, ctx)
        if (rules.videoAds && obj.has("ads_info")) removeKeys(obj, VIDEO_AD_KEYS, ctx)

        val keys = obj.keys().asSequence().toList()
        for (key in keys) {
            val value = obj.opt(key)
            if (value is JSONArray) {
                when {
                    rules.disableStories && ctx.method.startsWith("stories.get") &&
                        (key == "items" || key == "stories") && value.length() > 0 -> {
                        obj.put(key, JSONArray())
                        ctx.changed = true
                        continue
                    }
                    key == "items" || key == "feed" || key == "news" -> filterArray(value, ctx)?.let {
                        obj.put(key, it)
                        ctx.changed = true
                    }
                }
            }
            walk(obj.opt(key), ctx, depth + 1)
        }
    }

    private fun removeKeys(obj: JSONObject, keys: Set<String>, ctx: Context) {
        for (k in keys) {
            val v = obj.opt(k) ?: continue
            // Only structured payloads are advertising descriptors; plain flags stay intact.
            if (v is JSONObject || v is JSONArray) {
                obj.remove(k)
                ctx.changed = true
            }
        }
    }

    /** Returns a filtered copy of [array], or null when nothing had to be removed. */
    private fun filterArray(array: JSONArray, ctx: Context): JSONArray? {
        var out: JSONArray? = null
        for (i in 0 until array.length()) {
            val item = array.opt(i)
            val drop = item is JSONObject && shouldDrop(item, ctx)
            if (drop && out == null) {
                out = JSONArray()
                for (j in 0 until i) out.put(array.opt(j))
            }
            if (!drop) out?.put(item)
        }
        return out
    }

    private fun shouldDrop(item: JSONObject, c: Context): Boolean {
        val rules = c.rules
        val type = item.optString("type", "")

        if (rules.adTypes && type.startsWith("ads_") && type != "ads_disabled") return true
        if (rules.markedAds && (isTruthy(item.opt("marked_as_ads")) || item.optJSONObject("ad_data") != null)) return true
        if (rules.promoChats) {
            val conv = item.optJSONObject("conversation")
            if (conv != null && isTruthy(conv.opt("is_promo"))) return true
        }
        if (!c.feed) return false

        if (type.isNotEmpty()) {
            if (type in rules.feedTypes) return true
            if (rules.feedTypePrefixes.any { type.startsWith(it) }) return true
        }
        val isPost = type.isEmpty() || type == "post" || type == "wall_photo"
        if (rules.reposts && isPost) {
            val history = item.optJSONArray("copy_history")
            if (history != null && history.length() > 0) return true
        }
        if (rules.keywords.isNotEmpty() && isPost && containsKeyword(item, rules.keywords)) return true
        return false
    }

    private fun containsKeyword(post: JSONObject, keywords: List<String>): Boolean {
        val text = StringBuilder(post.optString("text", ""))
        post.optJSONArray("copy_history")?.let { h ->
            for (i in 0 until h.length()) h.optJSONObject(i)?.let { text.append('\n').append(it.optString("text", "")) }
        }
        if (text.isEmpty()) return false
        val lower = text.toString().lowercase(Locale.ROOT)
        return keywords.any { it.isNotEmpty() && lower.contains(it) }
    }

    private fun isTruthy(v: Any?): Boolean = when (v) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v == "1" || v.equals("true", ignoreCase = true)
        else -> false
    }

    /** Test hook: evaluates [shouldDrop] for an arbitrary method. */
    internal fun dropFor(method: String, item: JSONObject, rules: FilterRules): Boolean =
        shouldDrop(item, Context(method.lowercase(Locale.ROOT), rules))

    /**
     * Diagnostic: summarises the item `type` values (and ad-flag markers) present in a response, so a
     * feed that "nothing matched" can reveal how its ads are actually labelled. Returns a short string.
     */
    fun describe(body: String): String {
        val root = runCatching { JSONTokener(body).nextValue() as? JSONObject }.getOrNull() ?: return "not-an-object"
        val types = LinkedHashMap<String, Int>()
        val flags = HashSet<String>()
        fun scan(node: Any?, depth: Int) {
            if (depth > MAX_DEPTH) return
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) scan(node.opt(i), depth + 1)
                is JSONObject -> {
                    node.optString("type", "").takeIf { it.isNotEmpty() }?.let { types[it] = (types[it] ?: 0) + 1 }
                    for (k in listOf("marked_as_ads", "ad_data", "ads_info", "ad", "is_ads", "ad_type", "is_promo")) {
                        if (node.has(k)) flags += k
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) scan(node.opt(keys.next()), depth + 1)
                }
            }
        }
        scan(root.opt("response") ?: root, 0)
        val top = types.entries.sortedByDescending { it.value }.take(25).joinToString(",") { "${it.key}:${it.value}" }
        return "types[$top] flags[${flags.joinToString(",")}]"
    }
}
