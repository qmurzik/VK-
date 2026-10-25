package com.qmurzik.tokskip.accessibility

import com.qmurzik.tokskip.core.detect.AdDetector
import com.qmurzik.tokskip.core.model.Caption
import com.qmurzik.tokskip.core.model.DetectionResult
import com.qmurzik.tokskip.core.model.VideoMeta
import java.util.Locale

/**
 * Text seen on screen while one video plays (OCR frames and caption nodes), stamped with the
 * playback position. Lines present in most frames — a permanent title, a watermark, a sticker —
 * are static: they count like the description (ad or not) but carry no timing, so they cannot
 * stretch a segment over the whole video.
 */
class LiveScreenText {
    private class Frame(val posMs: Long, val spanMs: Long, val lines: List<String>, val tokens: List<Set<String>>)

    private val frames = ArrayList<Frame>()

    val size: Int get() = frames.size

    fun reset() = frames.clear()

    fun add(posMs: Long, lines: List<String>, spanMs: Long) {
        if (frames.size >= MAX_FRAMES) frames.removeAt(0)
        frames += Frame(posMs, spanMs, lines, lines.map(::tokens))
    }

    fun meta(base: VideoMeta): VideoMeta {
        val static = staticLines()
        val captions = frames.flatMap { f ->
            f.lines.indices.filter { i -> static.none { similar(it.second, f.tokens[i]) } }
                .map { i -> Caption(f.posMs, f.posMs + f.spanMs, f.lines[i]) }
        }
        return base.copy(captions = base.captions + captions, onScreenText = base.onScreenText + static.map { it.first })
    }

    fun detect(detector: AdDetector, base: VideoMeta): DetectionResult = detector.detect(meta(base))

    /** Lines (text, tokens) that appear in ≥ 60 % of at least [MIN_FRAMES_FOR_STATIC] frames. */
    private fun staticLines(): List<Pair<String, Set<String>>> {
        if (frames.size < MIN_FRAMES_FOR_STATIC) return emptyList()
        val out = ArrayList<Pair<String, Set<String>>>()
        for (f in frames) for ((i, t) in f.tokens.withIndex()) {
            if (t.isEmpty() || out.any { similar(it.second, t) }) continue
            val hits = frames.count { other -> other.tokens.any { similar(it, t) } }
            if (hits >= frames.size * 0.6) out += f.lines[i] to t
        }
        return out
    }

    private fun tokens(line: String): Set<String> =
        line.lowercase(Locale.ROOT).replace('ё', 'е').split(Regex("[^\\p{L}\\p{N}@_]+")).filter { it.length >= 2 }.toSet()

    private fun similar(a: Set<String>, b: Set<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        val inter = a.count { it in b }
        return inter.toFloat() / (a.size + b.size - inter) >= 0.6f
    }

    private companion object {
        const val MAX_FRAMES = 400
        const val MIN_FRAMES_FOR_STATIC = 6
    }
}
