package com.qmurzik.vkenhancer.ui

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.qmurzik.vkenhancer.common.Keys

/**
 * Exposes the module's local settings to the VK process.
 *
 * Rootless loaders (LSPatch/Vector) do not deliver the libxposed remote-preferences store to a
 * standalone settings app, so the UI writes to a plain [android.content.SharedPreferences] file and
 * this provider lets the injected hook pull those values (and observe changes) over normal IPC —
 * no root, no cross-sandbox file access. The provider is exported with no permission: it serves only
 * this module's own feature flags, which are not sensitive.
 */
class ConfigProvider : ContentProvider() {

    private val prefs get() = context!!.getSharedPreferences(Keys.PREFS_LOCAL, Context.MODE_PRIVATE)

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = when (method) {
        METHOD_GET_ALL -> snapshot()
        METHOD_PUT -> { apply(extras); notifyChanged(); Bundle.EMPTY }
        METHOD_REMOVE -> { arg?.let { prefs.edit().remove(it).apply() }; notifyChanged(); Bundle.EMPTY }
        else -> null
    }

    /** Serialises every stored preference into a [Bundle], preserving its type. */
    private fun snapshot(): Bundle {
        val out = Bundle()
        for ((key, value) in prefs.all) when (value) {
            is Boolean -> out.putBoolean(key, value)
            is Int -> out.putInt(key, value)
            is Long -> out.putLong(key, value)
            is Float -> out.putFloat(key, value)
            is String -> out.putString(key, value)
            is Set<*> -> out.putStringArrayList(key, ArrayList(value.map { it.toString() }))
            else -> {}
        }
        return out
    }

    /** Applies a typed [Bundle] of changes; a key mapped to null is removed. */
    private fun apply(extras: Bundle?) {
        extras ?: return
        val editor = prefs.edit()
        for (key in extras.keySet()) {
            when (val value = extras.get(key)) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is ArrayList<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
                else -> {}
            }
        }
        editor.apply()
    }

    private fun notifyChanged() {
        runCatching { context!!.contentResolver.notifyChange(CONTENT_URI, null) }
    }

    // Unused table operations.
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0

    companion object {
        const val METHOD_GET_ALL = "getAll"
        const val METHOD_PUT = "put"
        const val METHOD_REMOVE = "remove"
        val CONTENT_URI: Uri = Uri.parse("content://${Keys.PROVIDER_AUTHORITY}")
    }
}
