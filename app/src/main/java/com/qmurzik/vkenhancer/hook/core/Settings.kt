package com.qmurzik.vkenhancer.hook.core

import android.content.SharedPreferences
import com.qmurzik.vkenhancer.common.Defaults
import com.qmurzik.vkenhancer.common.Keys
import com.qmurzik.vkenhancer.hook.net.BlockRules
import com.qmurzik.vkenhancer.hook.net.FilterRules
import com.qmurzik.vkenhancer.hook.net.NetRules
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Read-only view of the module settings inside VK.
 *
 * LSPosed pushes preference changes into the hooked process, so derived rule objects are rebuilt on
 * every change and the hooks always read an immutable snapshot.
 */
class Settings(private val prefs: SharedPreferences?) {
    @Volatile var filterRules = FilterRules(); private set
    @Volatile var blockRules = BlockRules(); private set
    @Volatile var debug = false; private set
    @Volatile var enabled = true; private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    // Remote preferences keep listeners weakly, so a strong reference has to live here.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        rebuild()
        listeners.forEach { runCatching(it) }
    }

    init {
        rebuild()
        prefs?.registerOnSharedPreferenceChangeListener(prefListener)
    }

    fun onChange(listener: () -> Unit) {
        listeners += listener
    }

    fun bool(key: String): Boolean = enabled && (prefs?.getBoolean(key, Defaults.bool(key)) ?: Defaults.bool(key))
    fun rawBool(key: String): Boolean = prefs?.getBoolean(key, Defaults.bool(key)) ?: Defaults.bool(key)
    fun string(key: String, def: String = ""): String = prefs?.getString(key, def) ?: def
    fun int(key: String, def: Int): Int = prefs?.getInt(key, def) ?: def
    fun long(key: String, def: Long): Long = prefs?.getLong(key, def) ?: def
    fun stringSet(key: String): Set<String> = prefs?.getStringSet(key, emptySet()) ?: emptySet()
    fun has(key: String): Boolean = prefs?.contains(key) == true

    private fun rebuild() {
        enabled = rawBool(Keys.MODULE_ENABLED)
        debug = rawBool(Keys.DEBUG_LOG)
        filterRules = buildFilterRules()
        blockRules = buildBlockRules()
    }

    private fun buildFilterRules(): FilterRules {
        val types = HashSet<String>()
        val prefixes = ArrayList<String>()
        if (bool(Keys.ADS_PROMO)) {
            types += listOf("promo_button", "videos_promo", "clips_ozon", "clips_shops", "youla_carousel",
                "youla_groups_block", "market_carousel", "market_groups_block", "ads_disabled")
        }
        if (bool(Keys.FEED_RECOMMENDATIONS)) {
            types += listOf("friends_recomm", "user_rec", "authors_rec", "liked_by_friends_groups", "interests",
                "expert_card", "tags_suggestions", "friends_entrypoints", "holiday_friends", "recommended_groups",
                "recommended_channels", "recommended_chats", "recommended_narratives")
        }
        if (bool(Keys.FEED_CLIPS)) {
            types += "clip"
            prefixes += "clips_"
        }
        if (bool(Keys.FEED_GAMES)) {
            types += listOf("games_carousel", "recommended_game", "recommended_games_carousel", "mini_apps_carousel",
                "recommended_mini_app", "app_widget", "achievement_game", "bookmark_game")
        }
        if (bool(Keys.FEED_MUSIC)) {
            types += listOf("audios_for_you_block", "playlists_for_you_block", "recommended_audios",
                "recommended_playlists", "recommended_artists")
        }
        if (bool(Keys.FEED_VIDEO)) {
            types += listOf("videos_for_you", "videos_for_you_block", "video_postcard")
        }
        if (bool(Keys.FEED_NEWS)) {
            types += listOf("dzen_block", "dzen_story_news", "dzen_top_stories", "digest", "textlive",
                "discover_media_block")
        }
        if (bool(Keys.FEED_POLLS)) {
            types += listOf("uxpolls_block", "uxpoll_block", "feedback_poll", "info_block", "animated_block",
                "generic_webview_block", "signals")
        }
        if (bool(Keys.FEED_STORIES)) {
            types += listOf("stories", "stories_interesting_block")
        }
        if (enabled) types += NetRules.parseList(string(Keys.FEED_CUSTOM_TYPES))

        return FilterRules(
            feedTypes = types,
            feedTypePrefixes = prefixes,
            adTypes = bool(Keys.ADS_FEED),
            markedAds = bool(Keys.ADS_MARKED),
            reposts = bool(Keys.FEED_REPOSTS),
            keywords = if (enabled) NetRules.parseList(string(Keys.FEED_KEYWORDS)) else emptyList(),
            storyAds = bool(Keys.ADS_STORIES),
            disableStories = bool(Keys.STORIES_DISABLE),
            promoChats = bool(Keys.ADS_PROMO_CHATS),
            videoAds = bool(Keys.ADS_VIDEO),
            recommendedPosts = bool(Keys.FEED_RECOMMENDATIONS),
        )
    }

    private fun buildBlockRules(): BlockRules {
        val noAnalytics = bool(Keys.PRIV_NO_ANALYTICS)
        return BlockRules(
            adHosts = bool(Keys.ADS_NETWORK),
            trackerHosts = noAnalytics,
            analyticsMethods = noAnalytics,
            noRead = bool(Keys.PRIV_NO_READ),
            noTyping = bool(Keys.PRIV_NO_TYPING),
            offline = bool(Keys.PRIV_OFFLINE),
            storiesIncognito = bool(Keys.PRIV_STORIES_INCOGNITO),
            customMethods = if (enabled) NetRules.parseList(string(Keys.PRIV_BLOCKED_METHODS)).toSet() else emptySet(),
            customHosts = if (enabled) NetRules.parseList(string(Keys.PRIV_BLOCKED_HOSTS)) else emptyList(),
        )
    }
}
