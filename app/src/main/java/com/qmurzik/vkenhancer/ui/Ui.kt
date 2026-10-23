package com.qmurzik.vkenhancer.ui

import android.content.Context
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.qmurzik.vkenhancer.common.ColorSlot
import com.qmurzik.vkenhancer.common.Defaults
import com.qmurzik.vkenhancer.common.Keys
import com.qmurzik.vkenhancer.common.ThemePresets

/** Small DSL that builds androidx preferences into [group]; keeps [SettingsFragment] declarative. */
class Ui(private val fragment: PreferenceFragmentCompat, private val group: PreferenceGroup) {
    private val ctx: Context get() = fragment.requireContext()

    private fun add(p: Preference) {
        p.isIconSpaceReserved = false
        group.addPreference(p)
    }

    fun switch(key: String, title: String, summary: String? = null) = add(
        SwitchPreferenceCompat(ctx).apply {
            this.key = key; this.title = title; summary?.let { this.summary = it }
            setDefaultValue(Defaults.bool(key))
        }
    )

    fun category(title: String, block: Ui.() -> Unit) {
        val cat = PreferenceCategory(ctx).apply { this.title = title; isIconSpaceReserved = false }
        group.addPreference(cat)
        Ui(fragment, cat).block()
    }

    fun sub(key: String, title: String, summary: String, block: Ui.() -> Unit) {
        val screen: PreferenceScreen = fragment.preferenceManager.createPreferenceScreen(ctx).apply {
            this.key = "screen_$key"; this.title = title; this.summary = summary; isIconSpaceReserved = false
        }
        group.addPreference(screen)
        Ui(fragment, screen).block()
    }

    fun edit(key: String, title: String, summary: String) = add(
        EditTextPreference(ctx).apply {
            this.key = key; this.title = title; dialogTitle = title
            setSummaryProvider { (it as EditTextPreference).text?.takeIf(String::isNotBlank) ?: summary }
        }
    )

    fun listPref(key: String, title: String, default: String, entries: List<Pair<String, String>>) = add(
        ListPreference(ctx).apply {
            this.key = key; this.title = title; dialogTitle = title
            this.entries = entries.map { it.first }.toTypedArray()
            entryValues = entries.map { it.second }.toTypedArray()
            setDefaultValue(default)
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
    )

    fun slider(key: String, title: String, default: Int, min: Int, max: Int, step: Int) = add(
        SeekBarPreference(ctx).apply {
            this.key = key; this.title = title
            this.min = min; this.max = max; seekBarIncrement = step
            setDefaultValue(default); showSeekBarValue = true
        }
    )

    fun tabs(key: String, title: String, summary: String) = add(
        MultiSelectListPreference(ctx).apply {
            this.key = key; this.title = title; dialogTitle = title; this.summary = summary
            entries = TAB_OPTIONS.map { it.first }.toTypedArray()
            entryValues = TAB_OPTIONS.map { it.second }.toTypedArray()
        }
    )

    fun action(title: String, summary: String, onClick: () -> Boolean) = add(
        Preference(ctx).apply {
            this.title = title; this.summary = summary
            setOnPreferenceClickListener { onClick() }
        }
    )

    fun presetList() = add(
        ListPreference(ctx).apply {
            key = Keys.THEME_PRESET; title = "Готовая тема"; dialogTitle = "Выберите тему"
            entries = ThemePresets.ALL.map { it.title }.toTypedArray()
            entryValues = ThemePresets.ALL.map { it.id }.toTypedArray()
            setDefaultValue(Defaults.THEME_PRESET)
            summaryProvider = Preference.SummaryProvider<ListPreference> { pref ->
                ThemePresets.byId(pref.value).let { "${it.title} — ${it.description}" }
            }
        }
    )

    fun colorSection() {
        val cat = PreferenceCategory(ctx).apply { title = "Свои цвета"; isIconSpaceReserved = false }
        group.addPreference(cat)
        val store = (fragment.requireActivity() as SettingsActivity).store()
        for (slot in ColorSlot.entries) {
            cat.addPreference(ColorPreference(ctx, slot, store))
        }
    }

    private companion object {
        val TAB_OPTIONS = listOf(
            "Клипы" to "clips", "Музыка" to "music", "Игры" to "games", "Сервисы (VK Apps)" to "vk_apps",
            "Друзья" to "friends", "Сообщества" to "groups", "Видео" to "video", "VK Pay" to "vk_pay",
            "Поиск" to "search", "SuperApp" to "superapp", "Обратная связь" to "feedback",
        )
    }
}
