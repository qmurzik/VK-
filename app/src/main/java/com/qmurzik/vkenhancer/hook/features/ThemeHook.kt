package com.qmurzik.vkenhancer.hook.features

import android.content.res.Resources
import android.util.TypedValue
import com.qmurzik.vkenhancer.common.ColorSlot
import com.qmurzik.vkenhancer.common.Keys
import com.qmurzik.vkenhancer.common.Palette
import com.qmurzik.vkenhancer.common.ThemePresets
import com.qmurzik.vkenhancer.hook.core.Env
import com.qmurzik.vkenhancer.hook.core.Hooks
import com.qmurzik.vkenhancer.hook.core.L
import java.util.Collections
import java.util.WeakHashMap

/**
 * Recolours VK by intercepting the framework call every themed widget funnels through:
 * [Resources.Theme.resolveAttribute]. When VK asks the theme for one of its colour tokens
 * (`vk_ui_*`, `vk_legacy_*`, `vkontakte_color_*`), we map the attribute's *name* to a semantic
 * [ColorSlot] and, if the active palette overrides that slot, rewrite the returned colour.
 *
 * Nothing here references a VK class, so it is completely version-independent: new builds only add
 * or rename attributes, and the name→slot classifier keeps matching the ones that still mean the
 * same thing.
 */
object ThemeHook {
    private val slotByAttr = HashMap<Int, ColorSlot?>()
    private val darkByTheme = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val reentryGuard = ThreadLocal.withInitial { false }

    private var lightPalette = Palette.EMPTY
    private var darkPalette = Palette.EMPTY
    private var active = false
    private var darkAttrId = 0

    fun install() {
        if (!Env.settings.bool(Keys.THEME_ENABLED)) return
        rebuildPalettes()
        Env.settings.onChange { rebuildPalettes() }

        val target = Hooks.method(
            Resources.Theme::class.java, "resolveAttribute",
            Int::class.javaPrimitiveType!!, TypedValue::class.java, Boolean::class.javaPrimitiveType!!
        ) ?: return

        Hooks.intercept(target) { chain ->
            val result = chain.proceed()
            if (active && result == true && !reentryGuard.get()) {
                runCatching { maybeOverride(chain.thisObject as Resources.Theme, chain.getArg(0) as Int, chain.getArg(1) as TypedValue) }
                    .onFailure { L.d { "theme override skipped: ${it.message}" } }
            }
            result
        }
        L.i("theme engine active")
    }

    private fun rebuildPalettes() {
        val s = Env.settings
        active = s.bool(Keys.THEME_ENABLED)
        val preset = ThemePresets.byId(s.string(Keys.THEME_PRESET, "none"))
        val scope = s.string(Keys.THEME_CUSTOM_SCOPE, Keys.SCOPE_BOTH)

        val custom = HashMap<ColorSlot, Int>()
        for (slot in ColorSlot.entries) {
            val key = Keys.colorKey(slot)
            if (s.has(key)) custom[slot] = s.int(key, 0)
        }

        lightPalette = merge(preset.light, if (scope != Keys.SCOPE_DARK) custom else emptyMap(), amoled = false)
        val amoled = s.bool(Keys.THEME_AMOLED)
        darkPalette = merge(preset.dark, if (scope != Keys.SCOPE_LIGHT) custom else emptyMap(), amoled)
        slotByAttr.clear()
        darkByTheme.clear()
    }

    private fun merge(base: Palette, custom: Map<ColorSlot, Int>, amoled: Boolean): Palette {
        val map = HashMap(base.colors)
        if (amoled) {
            map[ColorSlot.BACKGROUND] = 0xFF000000.toInt()
            map[ColorSlot.CARD] = 0xFF000000.toInt()
            map[ColorSlot.HEADER] = 0xFF000000.toInt()
            map[ColorSlot.TABBAR] = 0xFF000000.toInt()
            map[ColorSlot.FIELD] = 0xFF0A0A0A.toInt()
            map[ColorSlot.SEPARATOR] = 0xFF1A1A1A.toInt()
        }
        map.putAll(custom) // user colours always win
        return Palette(map)
    }

    private fun maybeOverride(theme: Resources.Theme, attrId: Int, value: TypedValue) {
        if (value.type < TypedValue.TYPE_FIRST_COLOR_INT || value.type > TypedValue.TYPE_LAST_COLOR_INT) return
        val slot = classify(attrId) ?: return
        val dark = isDark(theme)
        val palette = if (dark) darkPalette else lightPalette
        var color = palette[slot] ?: return
        if (ColorSlot.isStateVariant(nameOf(attrId))) color = tintVariant(color, dark)
        value.data = color
    }

    private fun classify(attrId: Int): ColorSlot? = slotByAttr.getOrPut(attrId) {
        val name = nameOf(attrId)
        if (name.isEmpty()) null else ColorSlot.classify(name)
    }

    /** Framework attributes live in the `android:` (0x01...) space; VK attributes are app resources. */
    private fun nameOf(attrId: Int): String = runCatching {
        if ((attrId ushr 24) < 0x7f) "" else resources().getResourceEntryName(attrId)
    }.getOrDefault("")

    private var cachedResources: Resources? = null
    private fun resources(): Resources {
        cachedResources?.let { return it }
        // VK's own resources carry the attr names; the app context is attached during package load.
        val res = AppContext.get()?.resources ?: Resources.getSystem()
        cachedResources = res
        return res
    }

    /** Detects a theme's brightness once, from VK's own resolved window background. */
    private fun isDark(theme: Resources.Theme): Boolean {
        darkByTheme[theme]?.let { return it }
        val dark = runCatching {
            reentryGuard.set(true)
            val tv = TypedValue()
            val id = backgroundAttr()
            val ok = id != 0 && theme.resolveAttribute(id, tv, true)
            if (ok && tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) luminance(tv.data) < 0.5
            else (Env.appInfo.let { AppContext.isNightMode() })
        }.getOrDefault(false)
        reentryGuard.set(false)
        darkByTheme[theme] = dark
        return dark
    }

    private fun backgroundAttr(): Int {
        if (darkAttrId != 0) return darkAttrId
        val res = resources()
        for (name in listOf("vk_ui_background_content", "vk_ui_background", "vk_legacy_background_content")) {
            val id = runCatching { res.getIdentifier(name, "attr", Env.appInfo.packageName) }.getOrDefault(0)
            if (id != 0) { darkAttrId = id; return id }
        }
        return 0
    }

    private fun luminance(color: Int): Double {
        val r = (color shr 16 and 0xFF) / 255.0
        val g = (color shr 8 and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** Pressed/hover variants are nudged so they stay visible against the base colour. */
    private fun tintVariant(color: Int, dark: Boolean): Int {
        val factor = if (dark) 1.18 else 0.90
        val a = color ushr 24
        val r = ((color shr 16 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = ((color shr 8 and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((color and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
