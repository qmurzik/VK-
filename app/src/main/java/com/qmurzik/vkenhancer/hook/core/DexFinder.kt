package com.qmurzik.vkenhancer.hook.core

import com.qmurzik.vkenhancer.BuildConfig
import com.qmurzik.vkenhancer.common.Keys
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Method
import java.util.Properties

/**
 * Locates obfuscated classes by *what they do* rather than by their name.
 *
 * Every lookup is a set of string constants that the target code must use (error messages,
 * protocol literals). Obfuscators rename classes and members but keep string constants, so these
 * signatures survive across VK releases. Results are cached per installed APK, so DexKit only runs
 * once after each VK update.
 */
object DexFinder {
    private const val CACHE_NAME = "vkenhancer_dexkit.properties"
    private const val NOT_FOUND = "-"

    private val cache = Properties()
    private var cacheFile: File? = null
    private var fingerprint = ""
    private var dirty = false
    private var bridge: DexKitBridge? = null
    private var bridgeFailed = false

    fun init() {
        val app = Env.appInfo
        val apk = File(app.sourceDir)
        fingerprint = listOf(
            apk.length(), apk.lastModified(), BuildConfig.VERSION_CODE, Env.settings.long(Keys.CACHE_STAMP, 0L)
        ).joinToString(":")
        val dir = File(app.dataDir, "cache").apply { mkdirs() }
        val file = File(dir, CACHE_NAME)
        cacheFile = file
        runCatching {
            if (file.exists()) file.inputStream().use { cache.load(it) }
        }
        if (cache.getProperty("fingerprint") != fingerprint) {
            L.i("DexKit cache is stale, rescanning ${app.packageName}")
            cache.clear()
            cache.setProperty("fingerprint", fingerprint)
            dirty = true
        }
    }

    private fun bridge(): DexKitBridge? {
        if (bridge != null || bridgeFailed) return bridge
        return try {
            System.loadLibrary("dexkit")
            DexKitBridge.create(Env.appInfo.sourceDir).also { bridge = it }
        } catch (t: Throwable) {
            bridgeFailed = true
            L.e("DexKit unavailable", t)
            null
        }
    }

    /** Finds the single class whose code references all of [strings]. */
    fun findClass(key: String, vararg strings: String, packages: List<String> = emptyList()): Class<*>? {
        val cached = cache.getProperty("c.$key")
        if (cached != null) {
            return if (cached == NOT_FOUND) null else runCatching { DexClass(cached).getInstance(Env.classLoader) }
                .onFailure { L.w("cached class $cached for $key is invalid", it) }.getOrNull()
        }
        val found = runCatching {
            bridge()?.findClass {
                if (packages.isNotEmpty()) searchPackages(packages)
                matcher { usingStrings(*strings) }
            }
        }.onFailure { L.e("findClass($key) failed", it) }.getOrNull()
        val hit = found?.singleOrNull() ?: found?.firstOrNull()
        if (found != null && found.size > 1) L.w("findClass($key): ${found.size} candidates, using ${hit?.name}")
        put("c.$key", hit?.descriptor)
        return hit?.let { runCatching { it.getInstance(Env.classLoader) }.getOrNull() }
    }

    /** Finds the single method whose code references all of [strings]. */
    fun findMethod(
        key: String,
        vararg strings: String,
        packages: List<String> = emptyList(),
        filter: (Method) -> Boolean = { true },
    ): Method? {
        val cached = cache.getProperty("m.$key")
        if (cached != null) {
            return if (cached == NOT_FOUND) null else runCatching { DexMethod(cached).getMethodInstance(Env.classLoader) }
                .onFailure { L.w("cached method $cached for $key is invalid", it) }.getOrNull()
        }
        val found = runCatching {
            bridge()?.findMethod {
                if (packages.isNotEmpty()) searchPackages(packages)
                matcher { usingStrings(*strings) }
            }
        }.onFailure { L.e("findMethod($key) failed", it) }.getOrNull().orEmpty()

        var result: Method? = null
        var descriptor: String? = null
        for (candidate in found) {
            if (!candidate.isMethod) continue
            val m = runCatching { candidate.getMethodInstance(Env.classLoader) }.getOrNull() ?: continue
            if (filter(m)) {
                result = m
                descriptor = candidate.descriptor
                break
            }
        }
        put("m.$key", descriptor)
        return result
    }

    private fun put(key: String, value: String?) {
        cache.setProperty(key, value ?: NOT_FOUND)
        dirty = true
    }

    /** Releases the native index and persists newly found signatures. */
    fun finish() {
        runCatching { bridge?.close() }
        bridge = null
        if (!dirty) return
        runCatching {
            cacheFile?.outputStream()?.use { cache.store(it, "VK Enhancer signature cache") }
            dirty = false
        }.onFailure { L.w("cannot persist DexKit cache", it) }
    }
}
