package com.qmurzik.vkenhancer.hook

import com.qmurzik.vkenhancer.common.Keys
import com.qmurzik.vkenhancer.hook.core.DexFinder
import com.qmurzik.vkenhancer.hook.core.Env
import com.qmurzik.vkenhancer.hook.core.L
import com.qmurzik.vkenhancer.hook.core.Settings
import com.qmurzik.vkenhancer.hook.features.AppContext
import com.qmurzik.vkenhancer.hook.features.FontHook
import com.qmurzik.vkenhancer.hook.features.NetworkHook
import com.qmurzik.vkenhancer.hook.features.ThemeHook
import com.qmurzik.vkenhancer.hook.features.VpnHook
import com.qmurzik.vkenhancer.hook.features.UiHook
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * Module entry point. Declared in `META-INF/xposed/java_init.list`; LSPosed instantiates it once per
 * VK process, then calls [attachFramework] (handled by the superclass) before any callback.
 *
 * All feature hooks are version-independent: they bind to Android framework methods and to the
 * shadeable OkHttp/VKUI *shapes*, never to a specific VK build.
 */
@Suppress("unused")
class ModuleEntry : XposedModule() {

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        Env.attach(this, param.processName)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != VK_PACKAGE || !param.isFirstPackage) return
        if (initialized) return
        initialized = true

        runCatching { setUp(param) }.onFailure {
            log(android.util.Log.ERROR, Env.TAG, "init failed in ${param.packageName}", it)
        }
    }

    private fun setUp(param: PackageReadyParam) {
        val prefs = runCatching { getRemotePreferences(Keys.PREFS_GROUP) }
            .onFailure { L.w("remote preferences unavailable, using defaults", it) }
            .getOrNull()
        val settings = Settings(prefs)
        Env.bind(param.classLoader, param.applicationInfo, settings)
        AppContext.set(AppContext.get())

        if (!settings.enabled) {
            L.i("module disabled by user; standing down in ${Env.processName}")
            return
        }
        L.i("VK Enhancer starting in process '${Env.processName}' (main=${Env.isMainProcess})")

        DexFinder.init()
        // Cheap framework-level hooks run in every VK process so blocking/theming is global.
        safe("vpn") { VpnHook.install() }
        safe("theme") { ThemeHook.install() }
        safe("network") { NetworkHook.install() }
        // Heavier, screen-facing tweaks only matter in the UI process.
        if (Env.isMainProcess) {
            safe("ui") { UiHook.install() }
            safe("font") { FontHook.install() }
        }
        DexFinder.finish()
    }

    private inline fun safe(name: String, block: () -> Unit) =
        runCatching(block).onFailure { L.e("feature '$name' failed to install", it) }

    private companion object {
        const val VK_PACKAGE = "com.vkontakte.android"

        @Volatile
        var initialized = false
    }
}
