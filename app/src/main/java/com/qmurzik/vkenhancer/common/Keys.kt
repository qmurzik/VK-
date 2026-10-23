package com.qmurzik.vkenhancer.common

/**
 * Preference keys shared between the settings UI (module process) and the hooks (VK process).
 * Values travel through LSPosed remote preferences, so only primitive types and string sets are used.
 */
object Keys {
    const val PREFS_GROUP = "vkenhancer"

    // Global
    const val MODULE_ENABLED = "module_enabled"

    // Ads
    const val ADS_FEED = "ads_feed"
    const val ADS_MARKED = "ads_marked"
    const val ADS_PROMO = "ads_promo"
    const val ADS_STORIES = "ads_stories"
    const val ADS_VIDEO = "ads_video"
    const val ADS_NETWORK = "ads_network"
    const val ADS_VIEWS = "ads_views"
    const val ADS_PROMO_CHATS = "ads_promo_chats"

    // Feed
    const val FEED_RECOMMENDATIONS = "feed_recommendations"
    const val FEED_RECO_POSTS = "feed_reco_posts"
    const val FEED_CLIPS = "feed_clips"
    const val FEED_GAMES = "feed_games"
    const val FEED_MUSIC = "feed_music"
    const val FEED_VIDEO = "feed_video"
    const val FEED_NEWS = "feed_news"
    const val FEED_POLLS = "feed_polls"
    const val FEED_STORIES = "feed_stories"
    const val FEED_REPOSTS = "feed_reposts"
    const val FEED_KEYWORDS = "feed_keywords"
    const val FEED_CUSTOM_TYPES = "feed_custom_types"
    const val STORIES_DISABLE = "stories_disable"

    // Privacy
    const val PRIV_NO_READ = "priv_no_read"
    const val PRIV_NO_TYPING = "priv_no_typing"
    const val PRIV_OFFLINE = "priv_offline"
    const val PRIV_STORIES_INCOGNITO = "priv_stories_incognito"
    const val PRIV_NO_ANALYTICS = "priv_no_analytics"
    const val PRIV_SCREENSHOTS = "priv_screenshots"
    const val PRIV_BLOCKED_METHODS = "priv_blocked_methods"
    const val PRIV_BLOCKED_HOSTS = "priv_blocked_hosts"
    const val NET_HIDE_VPN = "net_hide_vpn"

    // Interface
    const val UI_HIDE_TABS = "ui_hide_tabs"
    const val UI_HIDE_BADGES = "ui_hide_badges"
    const val UI_HIDE_IDS = "ui_hide_ids"
    const val UI_INSPECTOR = "ui_inspector"
    const val UI_KEEP_SCREEN_ON = "ui_keep_screen_on"

    // Theme
    const val THEME_ENABLED = "theme_enabled"
    const val THEME_PRESET = "theme_preset"
    const val THEME_AMOLED = "theme_amoled"
    const val THEME_MATERIAL_YOU = "theme_material_you"
    const val THEME_BARS = "theme_bars"
    const val THEME_CUSTOM_SCOPE = "theme_custom_scope"

    /** Custom colour overrides: one key per [ColorSlot], stored as ARGB int, absent when unset. */
    fun colorKey(slot: ColorSlot) = "color_" + slot.id

    // Fonts
    const val FONT_FAMILY = "font_family"
    const val FONT_SCALE = "font_scale"
    const val FONT_CUSTOM_FILE = "custom_font.ttf"
    const val FONT_CUSTOM_STAMP = "font_custom_stamp"

    // Advanced
    const val DEBUG_LOG = "debug_log"
    const val CACHE_STAMP = "cache_stamp"

    const val SCOPE_BOTH = "both"
    const val SCOPE_LIGHT = "light"
    const val SCOPE_DARK = "dark"
}

/** Default values, kept in one place so the UI and hooks never disagree. */
object Defaults {
    private val TRUE_BY_DEFAULT = setOf(
        Keys.MODULE_ENABLED,
        Keys.ADS_FEED, Keys.ADS_MARKED, Keys.ADS_PROMO, Keys.ADS_STORIES, Keys.ADS_VIDEO,
        Keys.ADS_NETWORK, Keys.ADS_VIEWS, Keys.ADS_PROMO_CHATS,
        Keys.FEED_RECOMMENDATIONS, Keys.FEED_GAMES, Keys.FEED_POLLS,
        Keys.PRIV_NO_ANALYTICS,
        Keys.NET_HIDE_VPN,
        Keys.THEME_BARS,
    )

    fun bool(key: String): Boolean = key in TRUE_BY_DEFAULT

    const val THEME_PRESET = "none"
    const val FONT_FAMILY = "default"
    const val FONT_SCALE = 100
}
