package com.qmurzik.vkenhancer.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.qmurzik.vkenhancer.common.Keys
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * Obtains the module's preference store.
 *
 * When LSPosed has activated the module, its [XposedService] delivers remote preferences shared with
 * the VK process. Until (or unless) that happens, a local file-backed store is used so the UI still
 * works — marked read-only, because local writes would not reach VK.
 */
class ServiceConn(context: Context) {
    val store = RemoteStore(localPrefs(context)).apply { readOnly = true }

    @Volatile var bound = false; private set
    private var service: XposedService? = null
    private val main = Handler(Looper.getMainLooper())
    private var onState: ((Boolean) -> Unit)? = null
    private val appContext = context.applicationContext

    fun observe(listener: (bound: Boolean) -> Unit) {
        onState = listener
        listener(bound)
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(s: XposedService) {
                service = s
                val remote = runCatching { s.getRemotePreferences(Keys.PREFS_GROUP) }.getOrNull()
                main.post {
                    if (remote != null) {
                        store.prefs = remote
                        store.readOnly = false
                        bound = true
                    }
                    onState?.invoke(bound)
                }
            }

            override fun onServiceDied(s: XposedService) {
                main.post {
                    service = null
                    bound = false
                    store.prefs = localPrefs(appContext)
                    store.readOnly = true
                    onState?.invoke(false)
                }
            }
        })
    }

    val xposedService: XposedService? get() = service

    private fun localPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(Keys.PREFS_GROUP + "_local", Context.MODE_PRIVATE)
}
