package com.qmurzik.vkenhancer.hook.features

import android.content.res.Resources
import android.graphics.Typeface
import com.qmurzik.vkenhancer.common.Defaults
import com.qmurzik.vkenhancer.common.Keys
import com.qmurzik.vkenhancer.hook.core.Env
import com.qmurzik.vkenhancer.hook.core.Hooks
import com.qmurzik.vkenhancer.hook.core.L
import java.io.File

/**
 * Global font family and size overrides.
 *
 * The size override rescales `scaledDensity` returned by [Resources.getDisplayMetrics], which every
 * `sp` measurement reads — a framework-level hook that does not touch VK code. The family override
 * swaps the process default typefaces and intercepts [Typeface.create].
 */
object FontHook {
    private var replacement: Typeface? = null
    private var scale = 1f

    fun install() {
        scale = (Env.settings.int(Keys.FONT_SCALE, Defaults.FONT_SCALE).coerceIn(50, 200)) / 100f
        replacement = resolveTypeface(Env.settings.string(Keys.FONT_FAMILY, Defaults.FONT_FAMILY))

        if (scale != 1f) installScale()
        replacement?.let { installFamily(it) }
    }

    private fun resolveTypeface(family: String): Typeface? = when (family) {
        "serif" -> Typeface.SERIF
        "mono" -> Typeface.MONOSPACE
        "sans" -> Typeface.SANS_SERIF
        "custom" -> loadCustom()
        else -> null
    }

    private fun loadCustom(): Typeface? {
        val file = File(File(Env.appInfo.dataDir, "files"), Keys.FONT_CUSTOM_FILE)
        if (!file.exists()) {
            // The module copies the user's font into the VK data dir on startup; see FontProvision.
            L.w("custom font not found at ${file.path}")
            return null
        }
        return runCatching { Typeface.createFromFile(file) }.onFailure { L.w("bad custom font", it) }.getOrNull()
    }

    private fun installScale() {
        val target = Hooks.method(Resources::class.java, "getDisplayMetrics") ?: return
        Hooks.after(target) { _, metrics ->
            (metrics as? android.util.DisplayMetrics)?.let { it.scaledDensity = it.density * scale }
            metrics
        }
    }

    private fun installFamily(tf: Typeface) {
        runCatching {
            setStatic("DEFAULT", tf)
            setStatic("DEFAULT_BOLD", Typeface.create(tf, Typeface.BOLD))
            replaceDefaultsArray(tf)
        }.onFailure { L.w("set default typeface failed", it) }

        val create = Hooks.method(Typeface::class.java, "create", Typeface::class.java, Int::class.javaPrimitiveType!!)
        if (create != null) {
            Hooks.intercept(create) { chain ->
                val style = chain.getArg(1) as Int
                runCatching { Typeface.create(tf, style) }.getOrNull() ?: chain.proceed()
            }
        }
        L.i("font family overridden")
    }

    private fun setStatic(field: String, value: Typeface) {
        val f = Typeface::class.java.getDeclaredField(field)
        f.isAccessible = true
        f.set(null, value)
    }

    private fun replaceDefaultsArray(tf: Typeface) {
        runCatching {
            val f = Typeface::class.java.getDeclaredField("sDefaults")
            f.isAccessible = true
            f.set(null, arrayOf(tf, Typeface.create(tf, Typeface.BOLD),
                Typeface.create(tf, Typeface.ITALIC), Typeface.create(tf, Typeface.BOLD_ITALIC)))
        }
    }
}
