package com.qmurzik.tokskip.core

import com.qmurzik.tokskip.core.detect.CaptionParser
import com.qmurzik.tokskip.core.model.Caption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionParserTest {
    @Test
    fun parsesVttWithSettingsAndTags() {
        val text = "WEBVTT\n\nNOTE x\n\n1\n00:00:01.500 --> 00:00:03.000 align:start\n<c.yellow>Привет</c>\nмир\n\n00:01:02.000 --> 00:01:04.250\nВторая"
        assertTrue(CaptionParser.looksLikeSubtitles(text))
        assertEquals(
            listOf(Caption(1_500, 3_000, "Привет мир"), Caption(62_000, 64_250, "Вторая")),
            CaptionParser.parse(text)
        )
    }

    @Test
    fun parsesSrt() {
        val text = "1\r\n00:00:02,000 --> 00:00:04,000\r\nСтрока\r\n\r\n2\r\n01:00:00,000 --> 01:00:01,000\r\nЧас"
        assertEquals(listOf(Caption(2_000, 4_000, "Строка"), Caption(3_600_000, 3_601_000, "Час")), CaptionParser.parse(text))
    }
}
