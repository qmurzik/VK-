package com.qmurzik.vkenhancer.ui

import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import com.qmurzik.vkenhancer.common.Defaults

/**
 * Bridges androidx Preferences to whatever [SharedPreferences] is active — the LSPosed remote
 * preferences when the module is bound, or a local fallback otherwise. Writes made here are
 * delivered straight into the VK process by the framework.
 */
class RemoteStore(@Volatile var prefs: SharedPreferences?) : PreferenceDataStore() {
    var readOnly: Boolean = false

    private fun canWrite() = prefs != null && !readOnly

    override fun putString(key: String, value: String?) {
        if (canWrite()) prefs!!.edit().putString(key, value).apply()
    }

    override fun putStringSet(key: String, values: MutableSet<String>?) {
        if (canWrite()) prefs!!.edit().putStringSet(key, values).apply()
    }

    override fun putInt(key: String, value: Int) {
        if (canWrite()) prefs!!.edit().putInt(key, value).apply()
    }

    override fun putLong(key: String, value: Long) {
        if (canWrite()) prefs!!.edit().putLong(key, value).apply()
    }

    override fun putBoolean(key: String, value: Boolean) {
        if (canWrite()) prefs!!.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String, defValue: String?): String? = prefs?.getString(key, defValue) ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        prefs?.getStringSet(key, defValues) ?: defValues
    override fun getInt(key: String, defValue: Int): Int = prefs?.getInt(key, defValue) ?: defValue
    override fun getLong(key: String, defValue: Long): Long = prefs?.getLong(key, defValue) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        prefs?.getBoolean(key, if (defValue) true else Defaults.bool(key)) ?: defValue

    fun remove(key: String) {
        if (canWrite()) prefs!!.edit().remove(key).apply()
    }

    fun contains(key: String): Boolean = prefs?.contains(key) == true
}
