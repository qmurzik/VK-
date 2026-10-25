package com.qmurzik.tokskip.xposed

import android.content.pm.ApplicationInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Method
import java.util.Properties

/**
 * Finds TikTok code by stable traits instead of obfuscated names: string constants a method uses,
 * or kept member names of SDK / model classes. Results are cached per installed TikTok APK so DexKit
 * runs only once after every update. A failed lookup is cached too and simply disables the feature.
 */
class Finder(private val app: ApplicationInfo, private val classLoader: ClassLoader, moduleVersion: Long) {
    private val cacheFile = File(File(app.dataDir, "cache").apply { mkdirs() }, "tokskip_dexkit.properties")
    private val cache = Properties()
    private var bridge: DexKitBridge? = null
    private var bridgeFailed = false
    private var dirty = false

    init {
        val apk = File(app.sourceDir)
        val fp = "${apk.length()}:${apk.lastModified()}:$moduleVersion"
        runCatching { if (cacheFile.exists()) cacheFile.inputStream().use { cache.load(it) } }
        if (cache.getProperty("fingerprint") != fp) {
            cache.clear(); cache.setProperty("fingerprint", fp); dirty = true
            L.i("new TikTok build, signatures will be rescanned")
        }
    }

    fun loadClass(name: String): Class<*>? = runCatching { Class.forName(name, false, classLoader) }.getOrNull()

    /** Tries [names] first, then the class declaring a zero-arg [methodName] (kept by keep rules). */
    fun findClass(key: String, names: List<String>, methodName: String, returnType: String? = null): Class<*>? {
        names.firstNotNullOfOrNull(::loadClass)?.let { return it }
        cache.getProperty("c.$key")?.let { return if (it == NONE) null else loadClass(it) }
        val hit = runCatching {
            bridge()?.findMethod {
                matcher {
                    name(methodName)
                    paramCount(0)
                    if (returnType != null) returnType(returnType)
                }
            }?.map { it.declaredClassName }?.distinct()
        }.onFailure { L.w("DexKit findClass($key)", it) }.getOrNull().orEmpty()
        if (hit.size > 1) L.w("findClass($key): ${hit.size} candidates, taking ${hit.first()}")
        put("c.$key", hit.firstOrNull())
        return hit.firstOrNull()?.let(::loadClass)
    }

    /** All methods whose code references every string of [strings]. */
    fun findMethodsUsing(key: String, vararg strings: String): List<Method> {
        cache.getProperty("m.$key")?.let { v ->
            if (v == NONE) return emptyList()
            return v.split('|').mapNotNull { d -> runCatching { DexMethod(d).getMethodInstance(classLoader) }.getOrNull() }
        }
        val found = runCatching {
            bridge()?.findMethod { matcher { usingStrings(*strings) } }?.filter { it.isMethod }
        }.onFailure { L.w("DexKit findMethodsUsing($key)", it) }.getOrNull().orEmpty().take(MAX_METHODS)
        put("m.$key", found.joinToString("|") { it.descriptor }.ifEmpty { null })
        return found.mapNotNull { runCatching { it.getMethodInstance(classLoader) }.getOrNull() }
    }

    private fun bridge(): DexKitBridge? {
        if (bridge != null || bridgeFailed) return bridge
        return try {
            System.loadLibrary("dexkit")
            DexKitBridge.create(app.sourceDir).also { bridge = it }
        } catch (t: Throwable) {
            bridgeFailed = true
            L.w("DexKit unavailable", t)
            null
        }
    }

    private fun put(key: String, value: String?) {
        cache.setProperty(key, value ?: NONE); dirty = true
    }

    fun finish() {
        runCatching { bridge?.close() }
        bridge = null
        if (dirty) runCatching { cacheFile.outputStream().use { cache.store(it, "TokSkip signatures") }; dirty = false }
    }

    private companion object {
        const val NONE = "-"
        const val MAX_METHODS = 8
    }
}
