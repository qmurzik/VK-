package com.qmurzik.tokskip.core.detect

import java.util.Locale

internal object Text {
    private val junk = Regex("[^\\p{L}\\p{N}#@%_\\-]+")
    private val spaces = Regex("\\s+")

    /** Lowercases, folds ё→е and collapses punctuation so patterns stay simple. */
    fun normalize(s: String): String =
        s.lowercase(Locale.ROOT).replace('ё', 'е').replace(junk, " ").replace(spaces, " ").trim()

    /** Left word boundary that works for Cyrillic (Java's \b is ASCII-centric). */
    const val L = "(?<![\\p{L}\\p{N}])"

    /** Right word boundary. */
    const val R = "(?![\\p{L}\\p{N}])"
}
