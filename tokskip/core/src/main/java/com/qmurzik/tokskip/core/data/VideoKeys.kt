package com.qmurzik.tokskip.core.data

import com.qmurzik.tokskip.core.detect.Text
import java.security.MessageDigest

/**
 * The accessibility mode cannot see TikTok's video id, so it keys videos by their visible text.
 * The root mode computes the same key from the feed metadata, so manual markup made in either mode
 * is found by the other.
 */
object VideoKeys {
    private const val PREFIX_LEN = 40
    private val hashtags = Regex("#\\S+")
    private val moreSuffix = Regex("(\\s|\\.)*(ещ[её]|more|подробнее)$")

    fun uiKey(author: String, description: String): String? {
        val desc = Text.normalize(description.replace(moreSuffix, "")).replace(hashtags, "").trim()
        val a = Text.normalize(author).removePrefix("@")
        val basis = if (desc.length >= 8) desc.take(PREFIX_LEN) else if (a.isNotEmpty()) "$a|$desc" else return null
        val digest = MessageDigest.getInstance("SHA-1").digest(basis.toByteArray())
        return "ui:" + digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
