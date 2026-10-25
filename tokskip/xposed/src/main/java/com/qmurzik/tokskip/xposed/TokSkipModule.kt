package com.qmurzik.tokskip.xposed

import android.app.Application
import android.content.Context
import com.qmurzik.tokskip.core.Constants
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * LSPosed entry (META-INF/xposed/java_init.list), scoped to the TikTok packages.
 *
 * Every feature is installed independently and fails closed: a missing class or method is logged,
 * that feature is switched off and TikTok keeps running unmodified.
 */
@Suppress("unused")
class TokSkipModule : XposedModule {
    constructor() : super()

    /** Some loaders instantiate the entry with (base, param); the framework still attaches itself. */
    constructor(@Suppress("UNUSED_PARAMETER") base: XposedInterface, @Suppress("UNUSED_PARAMETER") param: ModuleLoadedParam) : this()

    private var processName = ""

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
        L.module = this
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName !in Constants.TIKTOK_PACKAGES || !param.isFirstPackage) return
        // Only the UI process plays videos; ":push", ":miniapp" etc. are left alone.
        if (processName.isNotEmpty() && processName != param.packageName) return
        if (started) return
        started = true
        runCatching { setUp(param) }.onFailure { L.w("инициализация не удалась — TikTok работает без изменений", it) }
    }

    private fun setUp(param: PackageReadyParam) {
        L.i("TokSkip в ${param.packageName} ($processName), API $apiVersion")
        val finder = Finder(param.applicationInfo, param.classLoader, BUILD)
        val status = linkedMapOf<String, Boolean>()
        status["лента"] = safe("лента") { FeedHook.install(finder) }
        status["субтитры"] = safe("субтитры") { CaptionHook.install(finder) }
        status["плеер"] = safe("плеер") { PlayerHook.install(finder) { null } }
        finder.finish()
        val summary = status.entries.joinToString { "${it.key}: ${if (it.value) "ок" else "нет"}" }
        L.i("хуки: $summary")
        hookApplication(summary)
    }

    /** Waits for TikTok's Application to get a Context for IPC and toasts. */
    private fun hookApplication(summary: String) {
        val onCreate = Application::class.java.getDeclaredMethod("onCreate")
        Hooks.after(onCreate) { chain, _ ->
            val app = chain.thisObject as? Context ?: return@after
            if (attached) return@after
            attached = true
            Bridge.attach(app, this)
            PlayerHook.attachContext(app)
            Bridge.ping(summary)
        }
    }

    private inline fun safe(name: String, block: () -> Boolean): Boolean =
        runCatching(block).onFailure { L.w("функция '$name' отключена", it) }.getOrDefault(false)

    private companion object {
        /** Bump when signatures change so cached DexKit results are rescanned. */
        const val BUILD = 1L
        @Volatile var started = false
        @Volatile var attached = false
    }
}
