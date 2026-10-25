package com.qmurzik.tokskip.app.ui

import com.qmurzik.tokskip.core.model.Segment

object Format {
    fun ms(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60) + (ms % 1000 / 100).let { if (it > 0) ".$it" else "" }

    fun segment(s: Segment) = "${ms(s.startMs)}–${ms(s.endMs)}"

    fun duration(ms: Long): String {
        val s = ms / 1000
        return when {
            s >= 3600 -> "${s / 3600} ч ${s / 60 % 60} мин"
            s >= 60 -> "${s / 60} мин ${s % 60} с"
            else -> "$s с"
        }
    }

    /** "0:12-0:27" per line (also "12-27" in seconds, "1:02.5"). Returns null on a malformed line. */
    fun parseSegments(text: String): List<Segment>? = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
        val parts = line.split('-', '–', '—').map { it.trim() }
        if (parts.size != 2) return null
        val a = parseTime(parts[0]) ?: return null
        val b = parseTime(parts[1]) ?: return null
        if (b <= a) return null
        Segment(a, b, 1f)
    }

    private fun parseTime(t: String): Long? {
        val p = t.split(':')
        return runCatching {
            val sec = p.last().replace(',', '.').toDouble()
            val min = if (p.size >= 2) p[p.size - 2].toLong() else 0
            val hr = if (p.size == 3) p[0].toLong() else 0
            ((hr * 3600 + min * 60) * 1000 + sec * 1000).toLong()
        }.getOrNull()
    }

    /** Accepts a raw aweme id or a TikTok link containing /video/<id>. */
    fun videoIdFrom(input: String): String {
        val t = input.trim()
        return Regex("/video/(\\d{8,})").find(t)?.groupValues?.get(1) ?: t
    }
}
