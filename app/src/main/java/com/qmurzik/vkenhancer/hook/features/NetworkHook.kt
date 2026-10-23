package com.qmurzik.vkenhancer.hook.features

import com.qmurzik.vkenhancer.hook.core.DexFinder
import com.qmurzik.vkenhancer.hook.core.Env
import com.qmurzik.vkenhancer.hook.core.Hooks
import com.qmurzik.vkenhancer.hook.core.L
import java.io.IOException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Hooks OkHttp at the interceptor level to block requests and rewrite responses.
 *
 * VK shades OkHttp but R8 keeps the [okhttp3.Interceptor] interface name (the client, `Request`,
 * `Response` and `ResponseBody` are obfuscated to `o`/`p`/`u`/`v`). We therefore depend only on the
 * `Interceptor`/`Chain` shapes: `Chain` has one no-arg method returning the request type and one
 * one-arg method (same type) returning the response type. An interceptor built from a [Proxy] is
 * appended to every `OkHttpClient.Builder`'s application-interceptor list on construction, so the
 * mechanism survives obfuscation and app updates.
 */
object NetworkHook {
    @Volatile private var everFired = false

    fun install() {
        if (Env.settings.blockRules.isEmpty && Env.settings.filterRules.isEmpty) return

        val interceptorIface = findInterceptorInterface() ?: run {
            L.w("OkHttp Interceptor interface not found; network features disabled"); return
        }
        val chainIface = interceptorIface.declaredClasses.firstOrNull { it.isInterface }
        if (chainIface == null) { L.w("OkHttp Chain interface not found"); return }

        val adapter = ChainAdapter(chainIface)
        if (!adapter.resolve()) { L.w("could not map OkHttp Chain methods"); return }

        val interceptor = Proxy.newProxyInstance(Env.classLoader, arrayOf(interceptorIface)) { _, method, args ->
            if (method.name == "intercept" && args?.size == 1) adapter.handle(args[0])
            else throw UnsupportedOperationException(method.name)
        }

        // Inject into the OkHttpClient directly. R8 inlines Builder.addInterceptor in VK, so the
        // Builder cannot be found by method signature; the client class is found instead by the
        // "Null interceptor: " literal in its constructor (which inlining cannot remove), restricted
        // to the okhttp3 package so an ad SDK's shaded copy is never matched.
        val clientCls = findClient() ?: run { L.w("OkHttp client not found"); return }
        injectIntoClient(clientCls, interceptorIface, interceptor)
        L.i("OkHttp interceptor installed on ${clientCls.name}")
    }

    private fun findInterceptorInterface(): Class<*>? {
        runCatching { Class.forName("okhttp3.Interceptor", false, Env.classLoader) }.getOrNull()?.let { return it }
        // Shaded fallback: the Chain carries OkHttp's unique assertion string (okhttp3 package only,
        // so an ad SDK's bundled OkHttp is never picked up).
        val chain = DexFinder.findClass("okhttp_chain", "must call proceed() exactly once", packages = listOf("okhttp3")) ?: return null
        return chain.enclosingClass ?: chain.interfaces.firstOrNull { it.isInterface }
    }

    private fun findClient(): Class<*>? {
        runCatching { Class.forName("okhttp3.OkHttpClient", false, Env.classLoader) }.getOrNull()?.let { return it }
        val client = DexFinder.findClass("okhttp_client", "Null interceptor: ", packages = listOf("okhttp3"))
        if (client != null && !client.name.startsWith("okhttp3.")) {
            L.w("ignoring non-okhttp3 client candidate ${client.name}"); return null
        }
        return client
    }

    /**
     * Prepends [interceptor] to the client's application-interceptor list on every construction.
     *
     * The client keeps several `List` fields (interceptors, networkInterceptors, protocols,
     * connectionSpecs). The application-interceptor list is detected by its element type — the first
     * `List` whose items are [interceptorIface] instances — which is robust against field reordering.
     * Prepending makes our interceptor run first, so it sees the fully decompressed response body.
     */
    private fun injectIntoClient(clientCls: Class<*>, interceptorIface: Class<*>, interceptor: Any) {
        val listFields = clientCls.declaredFields
            .filter { java.util.List::class.java.isAssignableFrom(it.type) }
            .onEach { it.isAccessible = true }
        if (listFields.isEmpty()) { L.w("no List field on ${clientCls.name}"); return }

        for (ctor in clientCls.declaredConstructors) {
            Hooks.intercept(ctor) { chain ->
                val r = chain.proceed()
                runCatching { inject(chain.thisObject, listFields, interceptorIface, interceptor) }
                    .onFailure { L.w("inject interceptor failed", it) }
                r
            }
        }
    }

    private fun inject(client: Any, listFields: List<java.lang.reflect.Field>, interceptorIface: Class<*>, interceptor: Any) {
        for (field in listFields) {
            @Suppress("UNCHECKED_CAST")
            val current = field.get(client) as? List<Any> ?: continue
            // Identify the application-interceptor list by its element type; skip protocols/specs.
            if (current.isNotEmpty() && !interceptorIface.isInstance(current[0])) continue
            if (current.any { Proxy.isProxyClass(it.javaClass) && interceptorIface.isInstance(it) }) return
            // An empty list can't be typed; still inject into the first List as a last resort only if
            // no typed list was found — handled by ordering below.
            if (current.isEmpty()) continue
            val merged = ArrayList<Any>(current.size + 1).apply { add(interceptor); addAll(current) }
            field.set(client, merged)
            L.i("interceptor injected into ${client.javaClass.name}.${field.name} (was ${current.size})")
            return
        }
        // No non-empty interceptor list — do nothing rather than risk injecting into protocols/specs.
        L.w("no application-interceptor list found on ${client.javaClass.name}")
    }

    /** Reflectively drives one `Chain`: read request URL, block or rewrite as configured. */
    private class ChainAdapter(private val chainIface: Class<*>) {
        private lateinit var requestMethod: Method
        private lateinit var proceedMethod: Method

        fun resolve(): Boolean {
            val req = chainIface.methods.firstOrNull { it.parameterTypes.isEmpty() && isRequestType(it.returnType) } ?: return false
            val proceed = chainIface.methods.firstOrNull {
                it.parameterTypes.size == 1 && it.parameterTypes[0] == req.returnType
            } ?: return false
            requestMethod = req
            proceedMethod = proceed
            return RequestModel.resolve(req.returnType)
        }

        private fun isRequestType(c: Class<*>): Boolean =
            c.name.startsWith("okhttp3.") && !c.isInterface && !c.isPrimitive &&
                c.declaredFields.any { it.type.name == "okhttp3.l" || it.type.name == "okhttp3.k" } ||
                c.name == "okhttp3.p"

        fun handle(chainObj: Any): Any? {
            val request = requestMethod.invoke(chainObj)
            val url = request?.let { RequestModel.url(it) }
            if (!everFired) {
                everFired = true
                val host = url?.let { com.qmurzik.vkenhancer.hook.net.NetRules.hostOf(it) }
                // One INFO line proving the interceptor is on VK's live HTTP client.
                L.i("OkHttp interceptor is LIVE; first request host=$host")
                Diag.toast("live", "перехватчик активен ($host)")
            }
            if (url != null) {
                val method = com.qmurzik.vkenhancer.hook.net.ApiFilter.methodFromUrl(url)
                if (method != null) L.d { "api $method" }
                ApiFilterBridge.blockReason(url)?.let { reason ->
                    L.d { "blocked [$reason] $url" }
                    throw IOException("VKEnhancer blocked request ($reason)")
                }
            }
            // proceed() legitimately throws IOException (timeouts, connection resets). Reflection wraps
            // it in InvocationTargetException; rethrow the real cause so OkHttp retries/handles it as
            // usual. Not unwrapping turned a normal network error into a fatal UndeclaredThrowableException.
            val response = try {
                proceedMethod.invoke(chainObj, request)
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.cause ?: e
            }
            if (url == null || response == null) return response
            return try {
                ResponseRewriter.rewrite(url, response) ?: response
            } catch (t: Throwable) {
                L.w("rewrite threw, passing original response", t); response
            }
        }
    }
}
