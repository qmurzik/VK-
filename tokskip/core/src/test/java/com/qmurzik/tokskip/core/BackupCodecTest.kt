package com.qmurzik.tokskip.core

import com.qmurzik.tokskip.core.data.BackupCodec
import com.qmurzik.tokskip.core.data.Converters
import com.qmurzik.tokskip.core.data.VideoEntry
import com.qmurzik.tokskip.core.data.VideoKeys
import com.qmurzik.tokskip.core.model.Segment
import com.qmurzik.tokskip.core.model.SegmentSource
import com.qmurzik.tokskip.core.settings.FallbackAction
import com.qmurzik.tokskip.core.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BackupCodecTest {
    private val manual = VideoEntry("7300000000000000001", "chef", "сырники", listOf(Segment(12_500, 27_500, 1f)), SegmentSource.MANUAL, true, 100)
    private val auto = manual.copy(segments = listOf(Segment(10_000, 20_000, 0.7f)), source = SegmentSource.AUTO, updatedAt = 200)

    @Test
    fun roundTrip() {
        val back = BackupCodec.decode(BackupCodec.encode(listOf(manual, auto.copy(videoId = "ui:abc"))))
        assertEquals(listOf(manual, auto.copy(videoId = "ui:abc")), back)
    }

    @Test
    fun manualBeatsNewerAuto() {
        assertSame(manual, BackupCodec.pick(manual, auto))
        assertSame(manual, BackupCodec.pick(auto, manual))
    }

    @Test
    fun segmentColumnRoundTrip() {
        val segs = listOf(Segment(1, 2, 0.5f), Segment(3, 4, 1f))
        assertEquals(segs, Converters.decodeSegments(Converters.encodeSegments(segs)))
        assertEquals(emptyList<Segment>(), Converters.decodeSegments(""))
    }

    @Test
    fun uiKeyIgnoresTruncationAndHashtags() {
        val full = VideoKeys.uiKey("@chef", "Готовим идеальные сырники за 10 минут #рецепт #еда")
        val truncated = VideoKeys.uiKey("chef", "Готовим идеальные сырники за 10 минут #рец... ещё")
        assertEquals(full, truncated)
        assertNotEquals(full, VideoKeys.uiKey("chef", "Совсем другой ролик про блины"))
    }

    @Test
    fun settingsJsonRoundTrip() {
        val s = Settings(fallbackAction = FallbackAction.SWIPE, phrases = listOf("а"), whitelistAuthors = listOf("x"), threshold = 0.4f)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }
}
