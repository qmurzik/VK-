package com.qmurzik.vkenhancer.common

/**
 * Single source of truth for how features are grouped. The dashboard renders a card per section
 * (with a live "N of M enabled" badge) and the settings screen shows the matching category, so the
 * two never drift apart. [boolKeys] lists the on/off keys counted for the badge; [screen] marks a
 * section that is its own sub-page (Theme/Fonts) rather than a flat category.
 */
object Sections {
    data class Section(
        val id: String,
        val emoji: String,
        val title: String,
        val subtitle: String,
        val boolKeys: List<String>,
        val screen: Boolean = false,
    )

    val ALL: List<Section> = listOf(
        Section("ads", "🛑", "Реклама", "Посты, истории, видео, промо, серверы", listOf(
            Keys.ADS_FEED, Keys.ADS_MARKED, Keys.ADS_PROMO, Keys.ADS_STORIES,
            Keys.ADS_VIDEO, Keys.ADS_PROMO_CHATS, Keys.ADS_NETWORK,
        )),
        Section("feed", "📰", "Лента", "Рекомендации, клипы, истории, репосты", listOf(
            Keys.FEED_RECOMMENDATIONS, Keys.FEED_RECO_POSTS, Keys.FEED_CLIPS, Keys.FEED_GAMES,
            Keys.FEED_MUSIC, Keys.FEED_VIDEO, Keys.FEED_NEWS, Keys.FEED_POLLS, Keys.FEED_STORIES,
            Keys.FEED_REPOSTS, Keys.STORIES_DISABLE,
        )),
        Section("feed_extra", "🧹", "Лента: что ещё скрыть", "Трансляции, статьи, дайджесты и ещё 19", Features.FEED.map { it.key }),
        Section("privacy", "🔒", "Приватность", "Онлайн, «прочитано», аналитика, VPN", listOf(
            Keys.PRIV_NO_READ, Keys.PRIV_NO_TYPING, Keys.PRIV_OFFLINE, Keys.PRIV_STORIES_INCOGNITO,
            Keys.PRIV_NO_ANALYTICS, Keys.NET_HIDE_VPN, Keys.PRIV_SCREENSHOTS,
        )),
        Section("privacy_net", "📡", "Сеть и телеметрия", "Блокировка трекинга и статистики", Features.METHODS.map { it.key }),
        Section("interface", "🎛", "Интерфейс", "Вкладки, вибрация, экран", listOf(
            Keys.UI_KEEP_SCREEN_ON, Keys.UI_NO_HAPTICS,
        )),
        Section("interface_hide", "🙈", "Скрыть элементы", "Строка историй, пометки, кнопки", Features.VIEWS.map { it.key }),
        Section("theme", "🎨", "Оформление и цвета", "15 тем, AMOLED, 16 цветовых слотов", listOf(Keys.THEME_ENABLED), screen = true),
        Section("fonts", "🔤", "Шрифты", "Семейство и размер текста", emptyList(), screen = true),
        Section("advanced", "⚙️", "Дополнительно", "Отладка, кэш, о модуле", listOf(Keys.DEBUG_LOG)),
    )

    fun byId(id: String?): Section? = ALL.firstOrNull { it.id == id }
}
