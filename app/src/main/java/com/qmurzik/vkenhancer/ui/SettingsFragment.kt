package com.qmurzik.vkenhancer.ui

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import com.qmurzik.vkenhancer.common.Defaults
import com.qmurzik.vkenhancer.common.Keys

/**
 * The whole settings tree, built in code so it stays in lock-step with [Keys] and needs no XML.
 * Nested [PreferenceScreen]s give the Theme and Font sections their own pages.
 */
class SettingsFragment : PreferenceFragmentCompat() {

    private val store get() = (requireActivity() as SettingsActivity).store()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = store
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        preferenceScreen = screen
        build(screen)
    }

    private fun build(screen: PreferenceScreen) = with(Ui(this, screen)) {
        switch(Keys.MODULE_ENABLED, "Модуль включён", "Главный выключатель всех функций")

        category("Реклама") {
            switch(Keys.ADS_FEED, "Реклама в ленте", "Скрывать рекламные посты (ads_*)")
            switch(Keys.ADS_MARKED, "Помеченные «реклама»", "Посты с пометкой marked_as_ads")
            switch(Keys.ADS_PROMO, "Промо-блоки", "Кнопки продвижения, Ozon, Юла, маркет")
            switch(Keys.ADS_STORIES, "Реклама в историях", "Вырезать рекламные вставки в историях")
            switch(Keys.ADS_VIDEO, "Реклама в видео", "Убирать пре-роллы и ad_info у видео")
            switch(Keys.ADS_PROMO_CHATS, "Промо-чаты", "Рекламные беседы в списке диалогов")
            switch(Keys.ADS_NETWORK, "Блокировать рекламные серверы", "myTarget, Yandex Ads, AppLovin и прочие")
        }

        category("Лента") {
            switch(Keys.FEED_RECOMMENDATIONS, "Рекомендации", "«Вам может быть интересно», рекомендации друзей и групп")
            switch(Keys.FEED_CLIPS, "Клипы", "Блоки клипов в ленте")
            switch(Keys.FEED_GAMES, "Игры и мини-приложения", "Карусели игр и мини-приложений")
            switch(Keys.FEED_MUSIC, "Музыкальные блоки", "«Для вас» плейлисты, артисты")
            switch(Keys.FEED_VIDEO, "Видео-блоки", "«Видео для вас» в ленте")
            switch(Keys.FEED_NEWS, "Дзен и дайджесты", "Блоки Дзен, дайджесты, discover")
            switch(Keys.FEED_POLLS, "Опросы и баннеры", "UX-опросы, инфо-блоки, анимированные блоки")
            switch(Keys.FEED_STORIES, "Истории в ленте", "Скрывать строку историй")
            switch(Keys.FEED_REPOSTS, "Репосты", "Прятать записи с репостами")
            switch(Keys.STORIES_DISABLE, "Полностью отключить истории", "Возвращать пустой список историй")
            edit(Keys.FEED_KEYWORDS, "Фильтр по словам", "Скрывать посты, содержащие слова (через запятую)")
            edit(Keys.FEED_CUSTOM_TYPES, "Свои типы постов", "Доп. значения type для скрытия")
        }

        category("Приватность") {
            switch(Keys.PRIV_NO_READ, "Не отправлять «прочитано»", "Читайте сообщения незаметно")
            switch(Keys.PRIV_NO_TYPING, "Скрыть «печатает…»", "Не показывать набор текста")
            switch(Keys.PRIV_OFFLINE, "Невидимый онлайн", "Не отправлять setOnline")
            switch(Keys.PRIV_STORIES_INCOGNITO, "Истории инкогнито", "Не отмечать истории просмотренными")
            switch(Keys.PRIV_NO_ANALYTICS, "Блокировать аналитику", "myTracker, AppMetrica, Crashlytics, stats.*")
            switch(Keys.PRIV_SCREENSHOTS, "Разрешить скриншоты", "Снимать защищённые экраны (чаты, истории)")
            edit(Keys.PRIV_BLOCKED_METHODS, "Блок. методы API", "Напр. account.setonline (через запятую)")
            edit(Keys.PRIV_BLOCKED_HOSTS, "Блок. хосты", "Доп. домены для блокировки")
        }

        category("Интерфейс") {
            tabs(Keys.UI_HIDE_TABS, "Скрыть вкладки меню", "Выберите разделы нижней панели")
            switch(Keys.UI_KEEP_SCREEN_ON, "Не гасить экран", "Держать экран включённым в ВК")
            edit(Keys.UI_HIDE_IDS, "Скрыть элементы по id", "Имена ресурсов id через запятую")
        }

        sub("theme", "Оформление и цвета", "Темы, акценты, AMOLED") {
            switch(Keys.THEME_ENABLED, "Включить кастомизацию", "Применять тему и цвета к ВК")
            presetList()
            switch(Keys.THEME_AMOLED, "AMOLED-чёрный", "Чистый чёрный фон в тёмной теме")
            switch(Keys.THEME_BARS, "Красить системные панели", "Статус-бар и панель навигации в цвет шапки")
            listPref(
                Keys.THEME_CUSTOM_SCOPE, "Где применять цвета", Keys.SCOPE_BOTH,
                entries = listOf("Обе темы" to Keys.SCOPE_BOTH, "Только светлая" to Keys.SCOPE_LIGHT, "Только тёмная" to Keys.SCOPE_DARK)
            )
            colorSection()
        }

        sub("fonts", "Шрифты", "Семейство и размер текста") {
            listPref(
                Keys.FONT_FAMILY, "Шрифт", Defaults.FONT_FAMILY,
                entries = listOf("Системный" to "default", "Без засечек" to "sans", "С засечками" to "serif",
                    "Моноширинный" to "mono", "Свой файл (custom_font.ttf)" to "custom")
            )
            slider(Keys.FONT_SCALE, "Размер шрифта, %", Defaults.FONT_SCALE, 50, 200, 5)
        }

        category("Дополнительно") {
            switch(Keys.DEBUG_LOG, "Подробный лог", "Писать отладочные сообщения в Xposed-лог")
            action("Сбросить кэш сигнатур", "Пересканировать классы ВК после обновления") {
                store.putLong(Keys.CACHE_STAMP, System.currentTimeMillis())
                true
            }
        }
    }

    /** Rebuilds the enabled/disabled state when the master switch flips. */
    override fun onResume() {
        super.onResume()
        applyMasterState(preferenceScreen)
    }

    private fun applyMasterState(screen: PreferenceScreen) {
        val enabled = store.getBoolean(Keys.MODULE_ENABLED, true)
        for (i in 0 until screen.preferenceCount) {
            val p = screen.getPreference(i)
            if (p.key != Keys.MODULE_ENABLED) p.isEnabled = enabled
        }
    }
}
