package com.qmurzik.vkenhancer.hook.features

import android.content.Context
import android.content.res.Configuration
import java.lang.ref.WeakReference

/** Holds the VK application [Context] so hooks can reach resources without a VK symbol. */
object AppContext {
    @Volatile private var ref: WeakReference<Context>? = null

    fun set(context: Context?) {
        if (context != null) ref = WeakReference(context.applicationContext ?: context)
    }

    fun get(): Context? {
        ref?.get()?.let { return it }
        // Fallback: the current Application, so hooks work before any Activity exists.
        return runCatching {
            val at = Class.forName("android.app.ActivityThread")
            val app = at.getMethod("currentApplication").invoke(null) as? Context
            app?.also { set(it) }
        }.getOrNull()
    }

    fun isNightMode(): Boolean {
        val ctx = get() ?: return false
        val mode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }
}
