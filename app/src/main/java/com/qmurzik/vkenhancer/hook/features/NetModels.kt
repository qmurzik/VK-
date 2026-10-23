package com.qmurzik.vkenhancer.hook.features

import com.qmurzik.vkenhancer.hook.core.Env
import com.qmurzik.vkenhancer.hook.core.L
import com.qmurzik.vkenhancer.hook.net.ApiFilter
import com.qmurzik.vkenhancer.hook.net.NetRules
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale

/** Reads the request URL from an obfuscated OkHttp `Request` (`okhttp3.p`). */
object RequestModel {
    @Volatile var ready = false; private set
    private var urlField: Field? = null
    private var urlToString: Method? = null

    /** [requestType] is OkHttp's Request class. Its HttpUrl field exposes the full URL via toString(). */
    fun resolve(requestType: Class<*>): Boolean {
        if (ready) return true
        // The URL field is the one whose type is okhttp3.l (HttpUrl); fall back to a non-String okhttp3 object.
        val field = requestType.declaredFields.firstOrNull { it.type.name == "okhttp3.l" }
            ?: requestType.declaredFields.firstOrNull {
                val t = it.type
                t.name.startsWith("okhttp3.") && t != String::class.java && !java.util.Map::class.java.isAssignableFrom(t) &&
                    runCatching { t.getMethod("toString") }.isSuccess
            }
            ?: return false
        field.isAccessible = true
        urlField = field
        urlToString = field.type.getMethod("toString")
        ready = true
        return true
    }

    fun url(request: Any): String? = runCatching {
        val httpUrl = urlField?.get(request) ?: return null
        urlToString?.invoke(httpUrl) as? String
    }.getOrNull()
}

/** Connects the live [NetRules] evaluation to the module settings. */
object ApiFilterBridge {
    fun blockReason(url: String): String? = NetRules.blockReason(url, Env.settings.blockRules)
}

/**
 * Buffers and rewrites JSON bodies of VK API responses (`okhttp3.u` / `okhttp3.v`).
 *
 * Only `/method/<name>` calls are buffered, so image and media traffic is never touched. When the
 * body changes it is replaced with a fresh [okhttp3.v] built from the filtered JSON; because the
 * original one-shot source is consumed while reading, a fresh body is installed even when nothing
 * was removed, keeping the response readable downstream.
 */
object ResponseRewriter {
    @Volatile private var resolved = false
    @Volatile private var usable = false
    private var bodyField: Field? = null
    private var contentTypeMethod: Method? = null
    private var stringMethod: Method? = null
    private var bytesMethod: Method? = null
    private var createMethod: Method? = null
    private var createBytesMethod: Method? = null
    private var bytesFirst = true

    private fun ensureResolved(response: Any) {
        if (resolved) return
        synchronized(this) {
            if (resolved) return
            resolved = true
            runCatching { resolve(response.javaClass) }.onFailure { L.w("response model resolve failed", it) }
        }
    }

    private fun resolve(responseType: Class<*>) {
        val bf = responseType.declaredFields.firstOrNull { it.type.name == "okhttp3.v" } ?: return
        bf.isAccessible = true
        val bodyCls = bf.type
        val ct = bodyCls.methods.firstOrNull { it.name == "contentType" && it.parameterTypes.isEmpty() } ?: return
        val str = bodyCls.methods.firstOrNull { it.name == "string" && it.parameterTypes.isEmpty() } ?: return
        val create = bodyCls.methods.firstOrNull {
            it.name == "create" && java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java
        } ?: return
        // For msgpack we round-trip raw bytes: ResponseBody.bytes() and a static create(byte[], MediaType)
        // (argument order varies across OkHttp versions, so remember which side the byte[] is on).
        bytesMethod = bodyCls.methods.firstOrNull { it.name == "bytes" && it.parameterTypes.isEmpty() }
        val createBytes = bodyCls.methods.firstOrNull {
            it.name == "create" && java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                it.parameterTypes.size == 2 && it.parameterTypes.any { p -> p == ByteArray::class.java }
        }
        if (createBytes != null) bytesFirst = createBytes.parameterTypes[0] == ByteArray::class.java
        createBytesMethod = createBytes
        bodyField = bf; contentTypeMethod = ct; stringMethod = str; createMethod = create
        usable = true
    }

    /** @return the (mutated) response when the body was buffered, or null to keep the original untouched. */
    fun rewrite(url: String, response: Any): Any? {
        val method = ApiFilter.methodFromUrl(url) ?: return null
        ensureResolved(response)
        val feedish = method.startsWith("execute") || method.contains("feed") || method.startsWith("newsfeed")
        if (!usable) { if (feedish) L.d { "rewrite $method: model not usable" }; return null }

        val body = bodyField?.get(response) ?: run { if (feedish) L.d { "rewrite $method: no body" }; return null }
        val contentType = runCatching { contentTypeMethod?.invoke(body) }.getOrNull()
        val ctStr = contentType?.toString()?.lowercase(Locale.ROOT) ?: ""
        if (ctStr.contains("msgpack")) return rewriteMsgpack(method, feedish, response, body, contentType)
        if (contentType != null) {
            if (!ctStr.contains("json") && !ctStr.contains("javascript") && !ctStr.contains("text/")) {
                if (feedish) L.d { "rewrite $method: skip content-type=$ctStr" }
                return null
            }
        }

        val original = runCatching { stringMethod?.invoke(body) as? String }.getOrNull()
            ?: run { if (feedish) L.d { "rewrite $method: body read failed" }; return null }
        if (feedish) L.d { "rewrite $method: read ${original.length} b, ct=$contentType" }
        val filtered = runCatching { ApiFilter.filter(method, original, Env.settings.filterRules) }
            .onFailure { L.w("filter($method) failed", it) }.getOrNull()
        if (filtered != null) {
            L.d { "filtered $method: ${original.length} -> ${filtered.length}" }
            Diag.toast("filter", "вырезано в $method (${original.length}→${filtered.length} б)")
        } else if (feedish) {
            // Only report feed-bearing endpoints as "unchanged" to keep the diagnostics readable.
            L.d { "rewrite $method: nothing matched; ${ApiFilter.describe(original)}" }
            Diag.toast("seen-$method", "ответ $method без изменений")
        }

        val payload = filtered ?: original
        val newBody = runCatching { createMethod?.invoke(null, payload, contentType) }.getOrNull() ?: return null
        return runCatching {
            bodyField?.set(response, newBody)
            response
        }.onFailure { L.w("install body failed", it) }.getOrNull()
    }

    /**
     * Filters a MessagePack response: decode to JSON, run [ApiFilter], and only when something was
     * removed re-encode to msgpack and install a fresh body (keeping the x-msgpack content type).
     */
    private fun rewriteMsgpack(method: String, feedish: Boolean, response: Any, body: Any, contentType: Any?): Any? {
        val createBytes = createBytesMethod ?: run { if (feedish) L.d { "rewrite $method: no create(byte[])" }; return null }
        val raw = runCatching { bytesMethod?.invoke(body) as? ByteArray }.getOrNull()
            ?: run { if (feedish) L.d { "rewrite $method: msgpack bytes read failed" }; return null }
        val json = com.qmurzik.vkenhancer.hook.net.MsgpackCodec.toJson(raw)
            ?: run { if (feedish) L.d { "rewrite $method: msgpack decode failed (${raw.size} b)" }; return reinstallBytes(response, contentType, raw, createBytes) }
        if (feedish) L.d { "rewrite $method: msgpack decoded ${raw.size} b -> json ${json.length}" }

        val filtered = runCatching { ApiFilter.filter(method, json, Env.settings.filterRules) }
            .onFailure { L.w("filter($method) failed", it) }.getOrNull()
        if (filtered == null) {
            if (feedish) {
                L.d { "rewrite $method: nothing matched (msgpack); ${com.qmurzik.vkenhancer.hook.net.ApiFilter.describe(json)}" }
                Diag.toast("seen-$method", "ответ $method без изменений (msgpack)")
            }
            return reinstallBytes(response, contentType, raw, createBytes) // body was consumed; restore it
        }
        val repacked = com.qmurzik.vkenhancer.hook.net.MsgpackCodec.fromJson(filtered)
            ?: run { L.w("msgpack re-encode failed for $method"); return reinstallBytes(response, contentType, raw, createBytes) }
        L.d { "filtered $method (msgpack): json ${json.length} -> ${filtered.length}, bytes ${raw.size} -> ${repacked.size}" }
        Diag.toast("filter", "вырезано в $method (msgpack)")
        return reinstallBytes(response, contentType, repacked, createBytes)
    }

    private fun reinstallBytes(response: Any, contentType: Any?, bytes: ByteArray, createBytes: Method): Any? {
        val args: Array<Any?> = if (bytesFirst) arrayOf(bytes, contentType) else arrayOf(contentType, bytes)
        val newBody = runCatching { createBytes.invoke(null, *args) }.getOrNull() ?: return null
        return runCatching { bodyField?.set(response, newBody); response }
            .onFailure { L.w("install msgpack body failed", it) }.getOrNull()
    }
}
