package com.qmurzik.vkenhancer.hook.features

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.qmurzik.vkenhancer.hook.core.Env

/**
 * On-screen diagnostics shown inside VK when verbose logging is enabled, so the behaviour of the
 * hooks can be observed without pulling logcat from the VK process. Throttled per key to avoid spam.
 */
object Diag {
    private val main = Handler(Looper.getMainLooper())
    private val lastShown = HashMap<String, Long>()
    private const val THROTTLE_MS = 2500L

    fun toast(key: String, msg: String) {
        if (!Env.settings.debug) return
        val now = System.currentTimeMillis()
        synchronized(lastShown) {
            val prev = lastShown[key] ?: 0L
            if (now - prev < THROTTLE_MS) return
            lastShown[key] = now
        }
        val ctx = AppContext.get() ?: return
        main.post { runCatching { Toast.makeText(ctx, "VKEnhancer: $msg", Toast.LENGTH_SHORT).show() } }
    }
}
