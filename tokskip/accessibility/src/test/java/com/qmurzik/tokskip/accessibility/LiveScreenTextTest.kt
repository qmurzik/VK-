package com.qmurzik.tokskip.accessibility

import com.qmurzik.tokskip.core.detect.AdDetector
import com.qmurzik.tokskip.core.model.VideoMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveScreenTextTest {
    private val detector = AdDetector()
    private val base = VideoMeta("ui:test", durationMs = 68_000)

    /** Replays the real OCR frames one per second, as the service would see them while watching. */
    @Test
    fun adIsRecognisedLiveWhenTheBannerAppears() {
        val frames = javaClass.classLoader!!.getResource("ocr_vpn_insert.txt")!!.readText().lines().filter { '|' in it }
        val live = LiveScreenText()
        var firstAdAt: Long? = null
        for (line in frames) {
            val pos = line.substringBefore('|').toLong() * 1000
            live.add(pos, listOf(line.substringAfter('|')), 1000)
            val now = live.detect(detector, base).segments.firstOrNull { pos >= it.startMs && pos <= it.endMs }
            if (now != null && firstAdAt == null) firstAdAt = pos
        }
        assertEquals(29_000L, firstAdAt)
        val final = live.detect(detector, base).segments
        assertEquals(1, final.size)
        assertTrue(final[0].startMs in 26_000..29_000 && final[0].endMs in 34_000..36_000)
    }

    @Test
    fun permanentStickerIsStaticNotASegment() {
        val live = LiveScreenText()
        for (t in 0 until 20) {
            val lines = mutableListOf("Ссылка в профиле")
            if (t == 5) lines += "Смотрите какой закат"
            live.add(t * 1000L, lines, 1000)
        }
        val r = live.detect(detector, base)
        assertTrue(r.segments.isEmpty())
        assertNull(r.segments.firstOrNull())
        assertTrue(live.meta(base).onScreenText.contains("Ссылка в профиле"))
    }
}
