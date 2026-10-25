package com.qmurzik.tokskip.core.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings and counters, persisted as JSON in private SharedPreferences and exposed as flows. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tokskip", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _stats = MutableStateFlow(loadStats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private fun load(): Settings = prefs.getString(KEY_SETTINGS, null)
        ?.let { runCatching { Settings.fromJson(it) }.getOrNull() } ?: Settings()

    private fun loadStats() = Stats(
        prefs.getLong("skippedVideos", 0), prefs.getLong("skippedMs", 0), prefs.getLong("flaggedVideos", 0)
    )

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        prefs.edit().putString(KEY_SETTINGS, next.toJson()).apply()
        _settings.value = next
    }

    @Synchronized
    fun addSkip(ms: Long) {
        val s = _stats.value
        val next = s.copy(skippedVideos = s.skippedVideos + 1, skippedMs = s.skippedMs + ms.coerceAtLeast(0))
        prefs.edit().putLong("skippedVideos", next.skippedVideos).putLong("skippedMs", next.skippedMs).apply()
        _stats.value = next
    }

    @Synchronized
    fun addFlagged() {
        val next = _stats.value.let { it.copy(flaggedVideos = it.flaggedVideos + 1) }
        prefs.edit().putLong("flaggedVideos", next.flaggedVideos).apply()
        _stats.value = next
    }

    @Synchronized
    fun resetStats() {
        prefs.edit().remove("skippedVideos").remove("skippedMs").remove("flaggedVideos").apply()
        _stats.value = Stats()
    }

    /** Last time the root module reported in from TikTok (0 = never). */
    var lastHookPing: Long
        get() = prefs.getLong("lastHookPing", 0)
        set(v) { prefs.edit().putLong("lastHookPing", v).apply() }

    var hookStatus: String
        get() = prefs.getString("hookStatus", "") ?: ""
        set(v) { prefs.edit().putString("hookStatus", v).apply() }

    private companion object {
        const val KEY_SETTINGS = "settings_json"
    }
}
