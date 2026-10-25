package com.qmurzik.tokskip.core.detect

import com.qmurzik.tokskip.core.model.Caption

/** Parses WebVTT and SRT subtitle text into [Caption]s. Tolerant to cue settings and tags. */
object CaptionParser {
    private val timing = Regex("((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3})\\s*-->\\s*((?:\\d+:)?\\d{1,2}:\\d{2}[.,]\\d{1,3})")
    private val tags = Regex("<[^>]+>")

    fun looksLikeSubtitles(text: String): Boolean = text.trimStart().startsWith("WEBVTT") || timing.containsMatchIn(text.take(2000))

    fun parse(text: String): List<Caption> {
        val out = mutableListOf<Caption>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var i = 0
        while (i < lines.size) {
            val m = timing.find(lines[i])
            if (m == null) { i++; continue }
            val start = toMs(m.groupValues[1])
            val end = toMs(m.groupValues[2])
            val body = StringBuilder()
            i++
            while (i < lines.size && lines[i].isNotBlank() && timing.find(lines[i]) == null) {
                if (body.isNotEmpty()) body.append(' ')
                body.append(lines[i].replace(tags, "").trim())
                i++
            }
            if (body.isNotBlank()) out += Caption(start, end, body.toString())
        }
        return out
    }

    fun toMs(t: String): Long {
        val parts = t.replace(',', '.').split(':')
        val sec = parts.last().toDouble()
        val min = parts.getOrNull(parts.size - 2)?.toLong() ?: 0
        val hr = if (parts.size == 3) parts[0].toLong() else 0
        return (hr * 3_600_000 + min * 60_000 + (sec * 1000).toLong())
    }
}
