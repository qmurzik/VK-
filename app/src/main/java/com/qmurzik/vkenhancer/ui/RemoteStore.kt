package com.qmurzik.vkenhancer.ui

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import com.qmurzik.vkenhancer.common.Defaults

/**
 * Backs the settings UI with a local [SharedPreferences] file that always persists. When the
 * libxposed service binds (real LSPosed), writes are mirrored into its remote store too, so both
 * delivery paths stay in sync. On rootless loaders the local file is read by the VK hook through
 * [ConfigProvider], so settings apply even though the remote store is never reachable here.
 */
class RemoteStore(private val local: SharedPreferences) : PreferenceDataStore() {

    @Volatile var remote: SharedPreferences? = null

    override fun putString(key: String, value: String?) = write { it.putString(key, value) }
    override fun putStringSet(key: String, values: MutableSet<String>?) = write { it.putStringSet(key, values) }
    override fun putInt(key: String, value: Int) = write { it.putInt(key, value) }
    override fun putLong(key: String, value: Long) = write { it.putLong(key, value) }
    override fun putBoolean(key: String, value: Boolean) = write { it.putBoolean(key, value) }

    override fun getString(key: String, defValue: String?): String? = local.getString(key, defValue) ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String> =
        // Never null: MultiSelectListPreference.setValues() calls addAll() and would NPE on null.
        (local.getStringSet(key, defValues) ?: defValues)?.toMutableSet() ?: mutableSetOf()
    override fun getInt(key: String, defValue: Int): Int = local.getInt(key, defValue)
    override fun getLong(key: String, defValue: Long): Long = local.getLong(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        local.getBoolean(key, if (defValue) true else Defaults.bool(key))

    fun remove(key: String) = write { it.remove(key) }

    fun contains(key: String): Boolean = local.contains(key)

    /** Applies [edit] to the local file and, when bound, to the remote store as well. */
    private inline fun write(edit: (SharedPreferences.Editor) -> Unit) {
        local.edit().also(edit).apply()
        remote?.let { it.edit().also(edit).apply() }
    }
}
