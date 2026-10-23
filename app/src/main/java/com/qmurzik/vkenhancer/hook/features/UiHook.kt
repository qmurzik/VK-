package com.qmurzik.vkenhancer.hook.features

import android.app.Activity
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.qmurzik.vkenhancer.common.Keys
import com.qmurzik.vkenhancer.hook.core.Env
import com.qmurzik.vkenhancer.hook.core.Hooks
import com.qmurzik.vkenhancer.hook.core.L

/**
 * Interface tweaks that need no VK symbols: window-level flags and resource-id driven view hiding.
 *
 * View hiding keys off Android *resource entry names* (`menu_clips`, `ad_container`, ...) which are
 * stable identifiers VK keeps across versions, resolved at runtime through the app's own resources.
 */
object UiHook {
    private val hiddenMenuByTab = mapOf(
        "clips" to "menu_clips",
        "games" to "menu_games",
        "vk_apps" to "menu_vk_apps",
        "music" to "menu_audios",
        "clips_tab" to "menu_clips",
        "friends" to "menu_friends",
        "groups" to "menu_groups",
        "video" to "menu_videos",
        "feedback" to "menu_feedback",
        "vk_pay" to "menu_vk_pay",
        "search" to "menu_search",
        "superapp" to "menu_superapp",
    )

    /** Extra ad/promo view ids hidden whenever found, independent of the feed JSON filter. */
    private val adViewIds = listOf(
        "ad_container", "ads_container", "ad_layout", "ad_banner_image", "advert_banner_container",
        "feed_optional_ad_content", "bottom_promo_container", "assistant_banner",
    )

    private var hiddenMenuIds: Set<Int> = emptySet()
    private var hiddenAdIds: Set<Int> = emptySet()

    fun install() {
        installWindowFlags()
        prepareHiddenIds()
        if (hiddenMenuIds.isNotEmpty() || hiddenAdIds.isNotEmpty()) installViewHiding()
    }

    private fun installWindowFlags() {
        val secure = Env.settings.bool(Keys.PRIV_SCREENSHOTS)
        val keepOn = Env.settings.bool(Keys.UI_KEEP_SCREEN_ON)
        val tintBars = Env.settings.bool(Keys.THEME_ENABLED) && Env.settings.bool(Keys.THEME_BARS)
        if (!secure && !keepOn && !tintBars) return

        val onCreate = Hooks.method(Activity::class.java, "onCreate", android.os.Bundle::class.java) ?: return
        Hooks.intercept(onCreate) { chain ->
            val r = chain.proceed()
            runCatching {
                val activity = chain.thisObject as Activity
                val window = activity.window ?: return@runCatching
                if (secure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (tintBars) tintSystemBars(window)
            }.onFailure { L.d { "activity tweak failed: ${it.message}" } }
            r
        }
    }

    private fun tintSystemBars(window: Window) {
        val header = ThemeColors.resolve(window.context) ?: return
        runCatching {
            window.statusBarColor = header
            window.navigationBarColor = header
        }
    }

    private fun prepareHiddenIds() {
        val res = AppContext.get()?.resources ?: return
        val pkg = Env.appInfo.packageName
        fun id(name: String) = runCatching { res.getIdentifier(name, "id", pkg) }.getOrDefault(0)

        val hiddenTabs = Env.settings.stringSet(Keys.UI_HIDE_TABS)
        hiddenMenuIds = hiddenTabs.mapNotNull { hiddenMenuByTab[it] }.map(::id).filter { it != 0 }.toSet()

        val custom = com.qmurzik.vkenhancer.hook.net.NetRules.parseList(Env.settings.string(Keys.UI_HIDE_IDS))
        val ads = if (Env.settings.bool(Keys.ADS_FEED)) adViewIds else emptyList()
        hiddenAdIds = (ads + custom).map(::id).filter { it != 0 }.toSet()
    }

    /** Collapses views with a hidden id right after they are laid out. */
    private fun installViewHiding() {
        val setId = Hooks.method(View::class.java, "onAttachedToWindow") ?: return
        val targets = hiddenMenuIds + hiddenAdIds
        Hooks.intercept(setId) { chain ->
            runCatching {
                val view = chain.thisObject as View
                if (view.id != View.NO_ID && view.id in targets && view.visibility != View.GONE) {
                    view.visibility = View.GONE
                }
            }
            chain.proceed()
        }
    }
}

/** Resolves the active header colour for system-bar tinting. */
object ThemeColors {
    fun resolve(context: android.content.Context): Int? = runCatching {
        val tv = android.util.TypedValue()
        val res = context.resources
        val pkg = Env.appInfo.packageName
        val id = res.getIdentifier("vk_ui_header_background", "attr", pkg)
        if (id != 0 && context.theme.resolveAttribute(id, tv, true)) tv.data else null
    }.getOrNull()
}
