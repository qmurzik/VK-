package com.qmurzik.tokskip.core.detect

import com.qmurzik.tokskip.core.model.Caption
import com.qmurzik.tokskip.core.model.DetectionResult
import com.qmurzik.tokskip.core.model.Segment
import com.qmurzik.tokskip.core.model.VideoMeta

/**
 * Finds sponsor integrations inside a video from its text: description, on-screen text and timed
 * captions.
 *
 * Algorithm:
 * 1. every caption is scored by all [AdSignal]s; lines scoring ≥ [DetectorConfig.minLineScore] are
 *    trigger lines;
 * 2. trigger lines closer than [DetectorConfig.maxGapMs] form one integration;
 * 3. the segment starts at the first trigger minus padding and ends after the last trigger, extended
 *    over following ad-vocabulary lines and cut at a "back to the topic" phrase, plus padding;
 * 4. confidence = noisy-OR of the line scores, boosted when the description carries a legal marker.
 *
 * Without captions a marker in the description still yields `hasAd = true` with no segments.
 */
class AdDetector(
    private val config: DetectorConfig = DetectorConfig(),
    extraSignals: List<AdSignal> = emptyList(),
) {
    private val signals: List<AdSignal> = listOf(
        DisclosureSignal(),
        TriggerPhraseSignal(),
        ProductSignal(),
        ContextSignal(),
        ReturnToTopicSignal(),
        ListSignal("brand", HitKind.BRAND, config.brands, 0.5f),
        ListSignal("phrase", HitKind.TRIGGER, config.phrases, 0.6f),
    ) + extraSignals

    private val whitelist = config.whitelistAuthors.map { it.trim().removePrefix("@").lowercase() }.toSet()

    private class Line(val caption: Caption, val hits: List<SignalHit>) {
        val score: Float = noisyOr(hits.filter { it.kind != HitKind.CONTEXT && it.kind != HitKind.RETURN }.map { it.weight })
        val isReturn = hits.any { it.kind == HitKind.RETURN }
        val isContext = hits.any { it.kind == HitKind.CONTEXT } || score > 0f
    }

    fun scan(normalizedText: String): List<SignalHit> = signals.flatMap { it.match(normalizedText) }

    fun detect(meta: VideoMeta): DetectionResult {
        if (meta.author.trim().removePrefix("@").lowercase() in whitelist) return DetectionResult.NONE

        val staticText = (listOf(meta.description) + meta.onScreenText).filter { it.isNotBlank() }
        val staticHits = staticText.flatMap { scan(Text.normalize(it)) }
            .filter { it.kind != HitKind.CONTEXT && it.kind != HitKind.RETURN }
        val disclosure = staticHits.filter { it.kind == HitKind.DISCLOSURE }.maxOfOrNull { it.weight } ?: 0f
        val staticScore = noisyOr(staticHits.map { it.weight })
        val reasons = staticHits.map { "описание: ${it.signal} «${it.matched}»" }.toMutableList()

        val lines = meta.captions.filter { it.text.isNotBlank() }.sortedBy { it.startMs }
            .map { Line(it, scan(Text.normalize(it.text))) }

        val segments = mutableListOf<Segment>()
        var i = 0
        while (i < lines.size) {
            if (lines[i].score < config.minLineScore) { i++; continue }
            // Grow the cluster of trigger lines.
            val first = i
            var last = i
            var j = i + 1
            while (j < lines.size && lines[j].caption.startMs - lines[last].caption.endMs <= config.maxGapMs) {
                if (lines[j].isReturn) break
                if (lines[j].score >= config.minLineScore) last = j
                j++
            }
            val cluster = (first..last).map { lines[it] }.filter { it.score >= config.minLineScore }
            var conf = noisyOr(cluster.map { it.score })
            if (disclosure > 0f) conf = 1f - (1f - conf) * (1f - disclosure * 0.5f)

            val start = (lines[first].caption.startMs - config.padStartMs).coerceAtLeast(0)
            val (endLine, returnStart) = extendTail(lines, last)
            var end = lines[endLine].caption.endMs + config.padEndMs
            if (returnStart != null) end = end.coerceAtMost(returnStart)
            end = end.coerceAtLeast(lines[last].caption.endMs)
            if (meta.durationMs > 0) end = end.coerceAtMost(meta.durationMs)

            if (conf >= config.threshold && end > start) {
                segments += Segment(start, end, conf)
                reasons += "сегмент ${start}–$end: " + cluster.flatMap { it.hits }
                    .filter { it.kind != HitKind.CONTEXT }.joinToString { "«${it.matched}»" }
            }
            i = maxOf(endLine, last) + 1
        }

        val merged = merge(segments)
        val overall = maxOf(staticScore, merged.maxOfOrNull { it.confidence } ?: 0f)
        val hasAd = merged.isNotEmpty() || staticScore >= config.threshold
        return DetectionResult(hasAd, merged, if (hasAd) overall else staticScore, reasons)
    }

    /** Walks past the last trigger while the talk stays "salesy"; returns the last ad line and a return-phrase start. */
    private fun extendTail(lines: List<Line>, last: Int): Pair<Int, Long?> {
        var end = last
        var k = last + 1
        var neutralRun = 0
        while (k < lines.size) {
            val line = lines[k]
            if (line.caption.startMs - lines[end].caption.endMs > TAIL_GAP_MS) break
            if (line.isReturn) return end to line.caption.startMs
            if (line.isContext) { end = k; neutralRun = 0 } else if (++neutralRun > 1) break
            k++
        }
        return end to null
    }

    private fun merge(list: List<Segment>): List<Segment> {
        val out = mutableListOf<Segment>()
        for (s in list.sortedBy { it.startMs }) {
            val prev = out.lastOrNull()
            if (prev != null && s.startMs <= prev.endMs) {
                out[out.lastIndex] = Segment(prev.startMs, maxOf(prev.endMs, s.endMs), maxOf(prev.confidence, s.confidence))
            } else out += s
        }
        return out
    }

    companion object {
        private const val TAIL_GAP_MS = 4_000L

        internal fun noisyOr(weights: List<Float>): Float =
            1f - weights.fold(1f) { acc, w -> acc * (1f - w.coerceIn(0f, 1f)) }
    }
}
