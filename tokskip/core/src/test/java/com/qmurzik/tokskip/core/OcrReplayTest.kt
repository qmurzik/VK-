package com.qmurzik.tokskip.core

import com.qmurzik.tokskip.core.detect.AdDetector
import com.qmurzik.tokskip.core.model.Caption
import com.qmurzik.tokskip.core.model.VideoMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real-world regression: a 68 s TikTok re-upload with burned-in word-by-word subtitles and a VPN
 * banner + voice-over inserted at ~28.5–34.5 s. The fixture is Tesseract (tessdata_fast rus+eng,
 * the models bundled in the app) run on one frame per second — noise included.
 */
class OcrReplayTest {
    private fun load(name: String): List<Caption> =
        javaClass.classLoader!!.getResource(name)!!.readText().lines().filter { '|' in it }.map { line ->
            val t = line.substringBefore('|').trim().toLong() * 1000
            Caption(t, t + 1000, line.substringAfter('|'))
        }

    @Test
    fun findsVpnInsertFromScreenText() {
        val r = AdDetector().detect(VideoMeta("ocr", captions = load("ocr_vpn_insert.txt"), durationMs = 68_000))
        assertEquals("segments: ${r.segments} reasons: ${r.reasons}", 1, r.segments.size)
        val s = r.segments[0]
        assertTrue("start ${s.startMs}", s.startMs in 26_000..29_000)
        assertTrue("end ${s.endMs}", s.endMs in 34_000..36_000)
        assertTrue("confidence ${s.confidence}", s.confidence >= 0.9f)
    }

    @Test
    fun shortBrandDoesNotMatchLongerWords() {
        val d = AdDetector(com.qmurzik.tokskip.core.detect.DetectorConfig(brands = listOf("пари")))
        val r = d.detect(VideoMeta("x", captions = listOf(Caption(0, 3000, "Гуляем по парку"), Caption(3000, 6000, "пара слов о погоде"))))
        assertTrue(r.segments.isEmpty())
    }
}
