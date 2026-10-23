package com.qmurzik.vkenhancer.hook.core

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.qmurzik.vkenhancer.common.Keys
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A [SharedPreferences] view, inside the VK process, over the settings app's [ConfigProvider].
 *
 * This is the delivery path that works on rootless loaders (LSPatch/Vector), where the libxposed
 * remote-preferences store never carries the user's choices. Values are pulled once over IPC and
 * refreshed live through a [ContentObserver], so toggles apply without reinstalling.
 *
 * Read-only: the hook never writes settings, so [edit] is unsupported.
 */
class ProviderPrefs private constructor(
    private val context: Context,
    initial: Map<String, Any?>,
) : SharedPreferences {

    @Volatile private var map: Map<String, Any?> = initial
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            map = pull(context) ?: map
            listeners.forEach { runCatching { it.onSharedPreferenceChanged(this@ProviderPrefs, null) } }
        }
    }

    init {
        runCatching {
            context.contentResolver.registerContentObserver(URI, true, observer)
        }
    }

    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) =
        (map[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int) = (map[key] as? Int) ?: defValue
    override fun getLong(key: String, defValue: Long) = (map[key] as? Long) ?: defValue
    override fun getFloat(key: String, defValue: Float) = (map[key] as? Float) ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = (map[key] as? Boolean) ?: defValue
    override fun contains(key: String) = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException("read-only")

    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.addIfAbsent(l)
    }

    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(l)
    }

    companion object {
        private val URI: Uri = Uri.parse("content://${Keys.PROVIDER_AUTHORITY}")

        /** Opens the provider-backed store, or null if the provider is unreachable from VK. */
        fun tryOpen(context: Context): ProviderPrefs? {
            val initial = pull(context) ?: return null
            return ProviderPrefs(context, initial)
        }

        /** Fetches the full settings snapshot; null means the provider could not be reached. */
        private fun pull(context: Context): Map<String, Any?>? {
            val bundle = runCatching {
                context.contentResolver.call(URI, "getAll", null, null)
            }.getOrNull() ?: return null
            val out = HashMap<String, Any?>(bundle.size())
            for (key in bundle.keySet()) {
                @Suppress("DEPRECATION")
                when (val v = bundle.get(key)) {
                    is ArrayList<*> -> out[key] = v.map { it.toString() }.toSet()
                    else -> out[key] = v
                }
            }
            return out
        }
    }
}
