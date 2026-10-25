package com.qmurzik.tokskip.xposed

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable

/** Logging into logcat, the LSPosed log and (through [Bridge]) the app's "Логи" screen. */
object L {
    const val TAG = "TokSkip"
    @Volatile var module: XposedModule? = null
    @Volatile var verbose = false

    fun i(msg: String) {
        Log.i(TAG, msg); runCatching { module?.log(Log.INFO, TAG, msg) }; Bridge.log(msg)
    }

    fun w(msg: String, t: Throwable? = null) {
        Log.w(TAG, msg, t); runCatching { module?.log(Log.WARN, TAG, msg, t) }
        Bridge.log("⚠ $msg" + (t?.let { ": $it" } ?: ""))
    }

    inline fun d(msg: () -> String) {
        if (verbose) { val m = msg(); Log.d(TAG, m); Bridge.log(m) }
    }
}

/** Hooks that can never take TikTok down: our exceptions are swallowed by the framework. */
object Hooks {
    fun intercept(target: Executable, hooker: XposedInterface.Hooker): XposedInterface.HookHandle? = try {
        L.module!!.hook(target).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker)
    } catch (t: Throwable) {
        L.w("hook failed: $target", t)
        null
    }

    /** Runs [block] after the original call, keeping its result. */
    inline fun after(target: Executable, crossinline block: (XposedInterface.Chain, Any?) -> Unit) =
        intercept(target) { chain ->
            val r = chain.proceed()
            try { block(chain, r) } catch (t: Throwable) { L.w("after-hook ${target.name}", t) }
            r
        }

    /** Runs [block] before the original call. */
    inline fun before(target: Executable, crossinline block: (XposedInterface.Chain) -> Unit) =
        intercept(target) { chain ->
            try { block(chain) } catch (t: Throwable) { L.w("before-hook ${target.name}", t) }
            chain.proceed()
        }
}
