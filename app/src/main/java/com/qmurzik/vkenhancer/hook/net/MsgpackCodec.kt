package com.qmurzik.vkenhancer.hook.net

import org.json.JSONArray
import org.json.JSONObject
import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker

/**
 * Converts between MessagePack bytes and `org.json` trees.
 *
 * Modern VK negotiates `application/x-msgpack` for feed responses, so [ApiFilter] (which works on
 * JSON) cannot see them directly. We decode msgpack to a JSON string, let [ApiFilter] run, and — only
 * when something was actually removed — re-encode the filtered JSON back to msgpack, preserving the
 * on-wire content type so VK's own msgpack parser keeps working.
 */
object MsgpackCodec {

    /** Decodes msgpack bytes to a JSON string, or null if it isn't a decodable map/array. */
    fun toJson(bytes: ByteArray): String? = runCatching {
        MessagePack.newDefaultUnpacker(bytes).use { it.unpackValue().toJson() }
    }.getOrNull()

    /** Encodes a JSON string back to msgpack bytes. */
    fun fromJson(json: String): ByteArray? = runCatching {
        val root: Any = when (val v = org.json.JSONTokener(json).nextValue()) {
            is JSONObject, is JSONArray -> v
            else -> return null
        }
        MessagePack.newDefaultBufferPacker().use { packer ->
            packAny(packer, root)
            packer.toByteArray()
        }
    }.getOrNull()

    private fun packAny(packer: MessagePacker, value: Any?) {
        when (value) {
            null, JSONObject.NULL -> packer.packNil()
            is JSONObject -> {
                packer.packMapHeader(value.length())
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    packer.packString(k)
                    packAny(packer, value.opt(k))
                }
            }
            is JSONArray -> {
                packer.packArrayHeader(value.length())
                for (i in 0 until value.length()) packAny(packer, value.opt(i))
            }
            is String -> packer.packString(value)
            is Boolean -> packer.packBoolean(value)
            is Int -> packer.packInt(value)
            is Long -> packer.packLong(value)
            is Double -> packer.packDouble(value)
            is Float -> packer.packDouble(value.toDouble())
            is Number -> packer.packLong(value.toLong())
            else -> packer.packString(value.toString())
        }
    }
}
