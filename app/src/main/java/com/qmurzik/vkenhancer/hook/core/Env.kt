package com.qmurzik.vkenhancer.hook.core

import android.content.pm.ApplicationInfo
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

/** Process-wide state of the injected module. Populated once by [com.qmurzik.vkenhancer.hook.ModuleEntry]. */
object Env {
    const val TAG = "VKEnhancer"

    lateinit var module: XposedModule
        private set
    lateinit var classLoader: ClassLoader
        private set
    lateinit var appInfo: ApplicationInfo
        private set
    lateinit var settings: Settings
        private set

    var processName: String = ""
        private set
    val isMainProcess: Boolean
        get() = processName == appInfo.packageName

    fun attach(module: XposedModule, processName: String) {
        this.module = module
        this.processName = processName
    }

    fun bind(classLoader: ClassLoader, appInfo: ApplicationInfo, settings: Settings) {
        this.classLoader = classLoader
        this.appInfo = appInfo
        this.settings = settings
    }
}

object L {
    // Messages go to the LSPosed module log (via the framework) AND to logcat, so they can be read
    // with `logcat -s VKEnhancer:V` from the VK process without opening the LSPosed manager.
    fun i(msg: String) {
        Log.i(Env.TAG, msg); runCatching { Env.module.log(Log.INFO, Env.TAG, msg) }
    }

    fun w(msg: String, t: Throwable? = null) {
        Log.w(Env.TAG, msg, t); runCatching { Env.module.log(Log.WARN, Env.TAG, msg, t) }
    }

    fun e(msg: String, t: Throwable? = null) {
        Log.e(Env.TAG, msg, t); runCatching { Env.module.log(Log.ERROR, Env.TAG, msg, t) }
    }

    /** Verbose logging, only when the user enabled it in the module settings. */
    inline fun d(msg: () -> String) {
        if (Env.settings.debug) {
            val m = msg(); Log.d(Env.TAG, m); runCatching { Env.module.log(Log.DEBUG, Env.TAG, m) }
        }
    }
}

/** Thin helpers over the libxposed interceptor-chain API. */
object Hooks {
    fun intercept(target: Executable, hooker: XposedInterface.Hooker): XposedInterface.HookHandle? = try {
        Env.module.hook(target).intercept(hooker)
    } catch (t: Throwable) {
        L.e("hook failed: $target", t)
        null
    }

    /** Runs [block] after the original call; its return value replaces the result. */
    inline fun after(target: Executable, crossinline block: (XposedInterface.Chain, Any?) -> Any?) =
        intercept(target) { chain -> block(chain, chain.proceed()) }

    /** Runs [block] before the original call; returning [Proceed] keeps the original arguments. */
    inline fun before(target: Executable, crossinline block: (XposedInterface.Chain) -> Any?) =
        intercept(target) { chain ->
            when (val r = block(chain)) {
                Proceed -> chain.proceed()
                is Args -> chain.proceed(r.values)
                is Result -> r.value
                else -> chain.proceed()
            }
        }

    object Proceed
    class Args(vararg val values: Any?)
    class Result(val value: Any?)

    fun method(cls: Class<*>, name: String, vararg params: Class<*>) = try {
        cls.getDeclaredMethod(name, *params).also { it.isAccessible = true }
    } catch (t: Throwable) {
        L.w("no method ${cls.name}.$name", t)
        null
    }
}
