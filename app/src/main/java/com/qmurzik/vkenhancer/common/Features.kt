package com.qmurzik.vkenhancer.common

/**
 * Data-driven catalog of optional features.
 *
 * Each toggle maps a preference key to the version-independent signal it controls — a set of feed
 * item `type` values, a set of VK API method names to cancel, or a set of Android resource-id names
 * to hide. Settings unions the enabled toggles into the rule objects, and the UI renders them
 * automatically, so new features are a one-line catalog entry rather than new plumbing.
 */
object Features {

    /** Hides matching entries/blocks from feed-like responses (newsfeed.getFeed, execute, ...). */
    data class FeedToggle(
        val key: String,
        val title: String,
        val summary: String,
        val types: List<String> = emptyList(),
        val prefixes: List<String> = emptyList(),
        val default: Boolean = false,
    )

    /** Cancels matching VK API requests before they hit the network. */
    data class MethodToggle(
        val key: String,
        val title: String,
        val summary: String,
        val methods: List<String>,
        val default: Boolean = false,
    )

    /** Collapses views whose Android resource-id name matches, wherever they appear. */
    data class ViewToggle(
        val key: String,
        val title: String,
        val summary: String,
        val ids: List<String>,
        val default: Boolean = false,
    )

    /** Extra feed content the user can optionally strip; all default off to keep the feed intact. */
    val FEED: List<FeedToggle> = listOf(
        FeedToggle("feed_lives", "Трансляции", "Прямые эфиры и блоки трансляций",
            types = listOf("live", "lives", "recommended_live_videos", "live_recommended")),
        FeedToggle("feed_articles", "Статьи", "Блоки статей и дайджесты статей",
            types = listOf("article", "articles", "attachment_article_big", "attachment_article_small")),
        FeedToggle("feed_digests", "Дайджесты", "«Самое интересное», тематические подборки",
            types = listOf("digest", "digest_header", "digest_grid", "digest_list_item", "digest_footer", "feed_digest")),
        FeedToggle("feed_memories", "Воспоминания", "«В этот день», напоминания о записях",
            types = listOf("memories", "memory", "on_this_day")),
        FeedToggle("feed_birthdays", "Дни рождения", "Блок с днями рождения друзей",
            types = listOf("birthdays", "birthday")),
        FeedToggle("feed_friends_entry", "Точки входа друзей", "«Добавить друзей», предложения дружбы",
            types = listOf("friends_entrypoints", "friends_recomm", "holiday_friends", "friends_tags_confirmation")),
        FeedToggle("feed_situational", "Ситуативные посты", "Создать пост по событию/настроению",
            types = listOf("situational_post", "situational_post_video", "create_post", "post_draft")),
        FeedToggle("feed_expert", "Экспертные карточки", "Блоки экспертов и советов",
            types = listOf("expert_card")),
        FeedToggle("feed_stickers_suggest", "Предложения стикеров", "Подсказки и промо стикерпаков",
            types = listOf("tags_suggestions", "tags_suggestions_info", "stickers_suggestions")),
        FeedToggle("feed_donut", "VK Donut", "Тизеры и промо платных подписок на сообщества",
            types = listOf("donut_post_placeholder", "donut_post_teaser"),
            prefixes = listOf("donut_")),
        FeedToggle("feed_mini_apps", "Мини-приложения", "Карусели сервисов и мини-приложений",
            types = listOf("mini_apps_carousel", "recommended_mini_app", "app_widget", "mini_app_recommended_v2")),
        FeedToggle("feed_reco_games", "Рекомендации игр", "Карусели и предложения игр",
            types = listOf("games_carousel", "recommended_game", "recommended_games_carousel", "achievement_game", "bookmark_game")),
        FeedToggle("feed_textlive", "Текстовые трансляции", "Блоки TextLive",
            types = listOf("textlive")),
        FeedToggle("feed_geo", "Гео-посты", "Карты и записи рядом",
            types = listOf("geo_posts_map", "geo_map", "geo_map_with_address", "geo_check_in")),
        FeedToggle("feed_info", "Инфо-блоки", "Служебные и информационные вставки",
            types = listOf("info_block", "animated_block", "generic_webview_block", "signals", "block_header")),
        FeedToggle("feed_market", "Товары и магазины", "Карусели маркета и рекомендации товаров",
            types = listOf("market_carousel", "market_groups_block", "product_carousel", "youla_carousel", "youla_groups_block")),
        FeedToggle("feed_video_blocks", "Видео-блоки", "«Клипы», «видео для вас», шортсы",
            types = listOf("video_postcard", "videos_for_you", "videos_for_you_block", "clips_block", "clips_autoplay",
                "clips_challenges", "clips_retention"),
            prefixes = listOf("clips_")),
        FeedToggle("feed_music_blocks", "Музыкальные блоки", "Плейлисты/артисты «для вас»",
            types = listOf("audios_for_you_block", "playlists_for_you_block", "recommended_audios",
                "recommended_playlists", "recommended_artists", "music_tracks_carousel")),
        FeedToggle("feed_groups_reco", "Рекомендации сообществ", "«Интересные страницы», каналы, чаты",
            types = listOf("recommended_groups", "recommended_channels", "recommended_chats",
                "recommended_narratives", "liked_by_friends_groups", "authors_rec", "user_rec")),
        FeedToggle("feed_dzen", "Дзен", "Блоки и истории Дзен",
            types = listOf("dzen_block", "dzen_story_news", "dzen_top_stories"),
            prefixes = listOf("dzen_")),
        FeedToggle("feed_polls", "Опросы и фидбек", "UX-опросы и формы обратной связи",
            types = listOf("uxpolls_block", "uxpoll_block", "feedback_poll", "feedback_block")),
        FeedToggle("feed_clips_all", "Все клипы", "Полностью убрать клипы из ленты",
            types = listOf("clip"), prefixes = listOf("clips_", "composite_")),
    )

    /** Additional privacy/anti-telemetry request blocks; all default off except where noted. */
    val METHODS: List<MethodToggle> = listOf(
        MethodToggle("blk_ad_events", "Не отправлять рекламные события", "adsint.*, ads.conversionHit/retargetingHit",
            methods = listOf("adsint.", "ads.conversionhit", "ads.retargetinghit"), default = true),
        MethodToggle("blk_video_stats", "Не отправлять статистику видео", "video.getStatsToken и трекинг просмотров",
            methods = listOf("video.getstatstoken", "video.trackevents", "stats.trackvideoevents")),
        MethodToggle("blk_story_stats", "Не отправлять статистику историй", "stories.getDetailedStats, просмотры",
            methods = listOf("stories.getdetailedstats", "stories.sendinteraction")),
        MethodToggle("blk_typing_calls", "Скрывать активность в звонках", "voip-индикаторы активности",
            methods = listOf("messages.setactivity", "calls.setparticipantsstate")),
        MethodToggle("blk_apps_track", "Не отправлять трекинг приложений", "apps.getTrackBridgeCallHandlers, trackVisitor",
            methods = listOf("apps.gettrackbridgecallhandlers", "stats.trackvisitor", "stats.trackinstalledapps")),
        MethodToggle("blk_logs", "Не загружать логи и бенчмарки", "stats.benchmark, trackUploadServerState",
            methods = listOf("stats.benchmark", "stats.trackuploadserverstate", "stats.trackcustomevents")),
        MethodToggle("blk_easteregg", "Отключить пасхалки/спецэффекты", "specials.getEasterEggs",
            methods = listOf("specials.geteastereggs")),
        MethodToggle("blk_splash", "Отключить сплэш-баннеры", "splashscreens.get (экраны-заставки с промо)",
            methods = listOf("splashscreens.get")),
        MethodToggle("blk_situational", "Не грузить ситуативные подсказки", "internal.getSituationalSuggest",
            methods = listOf("internal.getsituationalsuggest")),
        MethodToggle("blk_adconfig", "Отключить конфиг рекламы", "apps.getAdvertisementConfig",
            methods = listOf("apps.getadvertisementconfig")),
    )

    /** View-hiding toggles keyed on stable resource-id names. */
    val VIEWS: List<ViewToggle> = listOf(
        ViewToggle("ui_hide_stories_bar", "Скрыть строку историй", "Убрать ленту историй сверху",
            ids = listOf("stories_block_title", "story_block", "stories_fake_block", "news_interesting_stories_block")),
        ViewToggle("ui_hide_ad_label", "Скрыть пометку «реклама»", "Плашки «Реклама»/disclaimer на постах",
            ids = listOf("ad_label", "ads_title", "ad_disclaimer", "inter_ad_label", "copy_ad_marker", "ad_marker")),
        ViewToggle("ui_hide_create_post", "Скрыть «Что у вас нового?»", "Панель создания поста в начале ленты",
            ids = listOf("posting_header", "create_post", "avatar_publish_story_cell", "post_draft")),
        ViewToggle("ui_hide_easy_promote", "Скрыть «Продвигать»", "Кнопки продвижения записей",
            ids = listOf("menu_ads_easy_promote", "action_button_easy_promote", "clip_feed_promotion_button")),
        ViewToggle("ui_hide_live_cover", "Скрыть промо эфиров", "Обложки и промо трансляций в видео",
            ids = listOf("live_label", "ongoing_live_label")),
    )

    fun allKeys(): Set<String> =
        (FEED.map { it.key } + METHODS.map { it.key } + VIEWS.map { it.key })
            .toSet()

    fun defaultsOn(): Set<String> =
        (FEED.filter { it.default } + METHODS.filter { it.default } + VIEWS.filter { it.default }).map { keyOf(it) }.toSet()

    private fun keyOf(t: Any): String = when (t) {
        is FeedToggle -> t.key
        is MethodToggle -> t.key
        is ViewToggle -> t.key
        else -> error("unknown toggle")
    }
}
