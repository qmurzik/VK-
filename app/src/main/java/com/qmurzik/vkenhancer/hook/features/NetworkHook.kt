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

        val builderCls = findBuilder(interceptorIface) ?: run { L.w("OkHttp builder not found"); return }
        appendOnConstruction(builderCls, interceptorIface, interceptor)
        L.i("OkHttp interceptor installed on ${builderCls.name}")
    }

    private fun findInterceptorInterface(): Class<*>? {
        runCatching { Class.forName("okhttp3.Interceptor", false, Env.classLoader) }.getOrNull()?.let { return it }
        // Shaded fallback: the Chain carries OkHttp's unique assertion string.
        val chain = DexFinder.findClass("okhttp_chain", "must call proceed() exactly once") ?: return null
        return chain.enclosingClass ?: chain.interfaces.firstOrNull { it.isInterface }
    }

    private fun findBuilder(interceptorIface: Class<*>): Class<*>? {
        runCatching { Class.forName("okhttp3.OkHttpClient\$Builder", false, Env.classLoader) }.getOrNull()?.let { return it }
        // A builder is the class that keeps a mutable List and an addInterceptor(Interceptor): Builder method.
        val candidate = DexFinder.findClass("okhttp_builder", "Null interceptor: ", "Null network interceptor: ")
        if (candidate != null) return candidate
        // Last resort: locate it from the interceptor interface usage by shape.
        return interceptorIface.classes.firstOrNull { c ->
            c.declaredMethods.any { it.parameterTypes.contentEquals(arrayOf(interceptorIface)) && it.returnType == c }
        }
    }

    /** Appends [interceptor] to the builder's first application-interceptor list after every construction. */
    private fun appendOnConstruction(builderCls: Class<*>, interceptorIface: Class<*>, interceptor: Any) {
        val listFields = builderCls.declaredFields.filter { java.util.List::class.java.isAssignableFrom(it.type) }
            .onEach { it.isAccessible = true }
        if (listFields.isEmpty()) { L.w("no interceptor list field on ${builderCls.name}"); return }
        // The first declared List is the application interceptors (runs once, sees the full body).
        val target = listFields.first()

        for (ctor in builderCls.declaredConstructors) {
            Hooks.intercept(ctor) { chain ->
                val r = chain.proceed()
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val list = target.get(chain.thisObject) as? MutableList<Any> ?: return@runCatching
                    if (list.none { Proxy.isProxyClass(it.javaClass) && interceptorIface.isInstance(it) }) list.add(interceptor)
                }.onFailure { L.w("append interceptor failed", it) }
                r
            }
        }
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
            if (url != null) {
                ApiFilterBridge.blockReason(url)?.let { reason ->
                    L.d { "blocked [$reason] $url" }
                    throw IOException("VKEnhancer blocked request ($reason)")
                }
            }
            val response = proceedMethod.invoke(chainObj, request)
            if (url == null || response == null) return response
            return ResponseRewriter.rewrite(url, response) ?: response
        }
    }
}
