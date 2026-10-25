package com.qmurzik.tokskip.xposed

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Reflection over TikTok model objects by *any of several* member names, with the Gson
 * `@SerializedName` value as a last resort. Lookups are cached per class.
 */
object Beans {
    private val methods = HashMap<String, Method?>()
    private val fields = HashMap<String, Field?>()

    /** Like getOrPut, but caches misses (null) too. */
    private inline fun <T> HashMap<String, T?>.memo(key: String, compute: () -> T?): T? = synchronized(this) {
        if (containsKey(key)) get(key) else compute().also { put(key, it) }
    }

    fun get(obj: Any?, vararg names: String): Any? {
        obj ?: return null
        for (n in names) {
            method(obj.javaClass, n)?.let { m -> return runCatching { m.invoke(obj) }.getOrNull() }
            field(obj.javaClass, n)?.let { f -> return runCatching { f.get(obj) }.getOrNull() }
        }
        return null
    }

    fun string(obj: Any?, vararg names: String): String? = get(obj, *names)?.toString()?.takeIf { it.isNotEmpty() }

    fun long(obj: Any?, vararg names: String): Long? = (get(obj, *names) as? Number)?.toLong()

    private fun method(cls: Class<*>, name: String): Method? = methods.memo("${cls.name}#$name") {
        var c: Class<*>? = cls
        while (c != null && c != Any::class.java) {
            c.declaredMethods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }?.let { it.isAccessible = true; return@memo it }
            c = c.superclass
        }
        null
    }

    /** A field by its Java name or by its `@SerializedName` value (json key). */
    private fun field(cls: Class<*>, name: String): Field? = fields.memo("${cls.name}.$name") {
        var c: Class<*>? = cls
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (f.name == name || serializedName(f) == name) { f.isAccessible = true; return@memo f }
            }
            c = c.superclass
        }
        null
    }

    private fun serializedName(f: Field): String? = f.declaredAnnotations.firstNotNullOfOrNull { a ->
        if (!a.annotationClass.java.name.endsWith("SerializedName")) null
        else runCatching { a.annotationClass.java.getMethod("value").invoke(a) as? String }.getOrNull()
    }

    /** Public zero-arg getters of a class, for the research log. */
    fun describe(cls: Class<*>): String = cls.methods.filter { it.parameterTypes.isEmpty() && it.name.startsWith("get") }
        .joinToString(",") { it.name.removePrefix("get") }.take(1500)
}
