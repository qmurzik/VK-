package com.qmurzik.tokskip.core

import com.qmurzik.tokskip.core.detect.AdDetector
import com.qmurzik.tokskip.core.detect.AdSignal
import com.qmurzik.tokskip.core.detect.CaptionParser
import com.qmurzik.tokskip.core.detect.DetectorConfig
import com.qmurzik.tokskip.core.detect.HitKind
import com.qmurzik.tokskip.core.detect.SignalHit
import com.qmurzik.tokskip.core.model.Caption
import com.qmurzik.tokskip.core.model.VideoMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdDetectorTest {

    private val detector = AdDetector()

    private fun vtt(text: String) = CaptionParser.parse(text.trimIndent())

    /** A cooking video with a sponsor block in the middle and an explicit return to the recipe. */
    private val recipeWithIntegration = vtt(
        """
        WEBVTT

        00:00.000 --> 00:04.000
        Всем привет, сегодня готовим идеальные сырники

        00:04.000 --> 00:09.000
        Берём творог пять процентов и одно яйцо

        00:09.000 --> 00:13.000
        Перемешиваем до однородности

        00:13.500 --> 00:17.000
        Кстати, сегодняшний ролик спонсирует Самокат

        00:17.000 --> 00:21.000
        По промокоду СЫРНИК скидка 30% на первый заказ

        00:21.000 --> 00:24.000
        Доставка за 15 минут прямо до двери

        00:24.000 --> 00:27.000
        Ссылка в шапке профиля, переходите

        00:27.500 --> 00:31.000
        Ну а теперь вернёмся к сырникам

        00:31.000 --> 00:36.000
        Добавляем муку и формируем шайбы
        """
    )

    @Test
    fun findsIntegrationBetweenTriggerAndReturnPhrase() {
        val r = detector.detect(VideoMeta("1", captions = recipeWithIntegration, durationMs = 36_000))
        assertTrue(r.hasAd)
        assertEquals(1, r.segments.size)
        val s = r.segments[0]
        assertEquals(12_500, s.startMs)            // first trigger (13.5 s) minus 1 s
        assertEquals(27_500, s.endMs)              // cut at "вернёмся"
        assertTrue("confidence ${s.confidence}", s.confidence >= 0.9f)
    }

    @Test
    fun endsOneSecondAfterLastTriggerWithoutReturnPhrase() {
        val captions = listOf(
            Caption(0, 5_000, "Сегодня разберём три ошибки в английском"),
            Caption(6_000, 10_000, "Но сначала: этот ролик при поддержке Skyeng"),
            Caption(10_000, 14_000, "Первый урок бесплатно по промокоду TOK"),
            Caption(20_000, 25_000, "Первая ошибка — путать since и for"),
            Caption(25_000, 30_000, "Since используется с моментом времени"),
        )
        val r = detector.detect(VideoMeta("2", captions = captions))
        assertEquals(1, r.segments.size)
        assertEquals(5_000, r.segments[0].startMs)
        assertEquals(15_000, r.segments[0].endMs)
    }

    @Test
    fun noAdInPlainVideo() {
        val captions = listOf(
            Caption(0, 4_000, "Смотрите какой закат над морем"),
            Caption(4_000, 8_000, "Мы приехали сюда на машине"),
            Caption(8_000, 12_000, "Завтра пойдём в горы"),
        )
        val r = detector.detect(VideoMeta("3", description = "Отпуск #море #горы", captions = captions))
        assertFalse(r.hasAd)
        assertTrue(r.segments.isEmpty())
    }

    @Test
    fun descriptionMarkerWithoutCaptionsFlagsVideoWithoutTimings() {
        val r = detector.detect(VideoMeta("4", description = "Мой новый образ 🔥 #реклама erid: 2VtzqwXyZ1"))
        assertTrue(r.hasAd)
        assertTrue(r.segments.isEmpty())
        assertTrue(r.confidence >= 0.9f)
    }

    @Test
    fun whitelistedAuthorIsIgnored() {
        val d = AdDetector(DetectorConfig(whitelistAuthors = setOf("@friend")))
        val r = d.detect(VideoMeta("5", author = "Friend", description = "#реклама", captions = recipeWithIntegration))
        assertFalse(r.hasAd)
    }

    @Test
    fun singleWeakTriggerStaysBelowThreshold() {
        val captions = listOf(
            Caption(0, 4_000, "Переходим ко второму уровню игры"),
            Caption(4_000, 8_000, "Здесь босс сильнее"),
        )
        val r = detector.detect(VideoMeta("6", captions = captions))
        assertFalse(r.hasAd)
    }

    @Test
    fun negatedMarkerIsNotAnAd() {
        val r = detector.detect(VideoMeta("7", description = "Это не реклама, просто люблю этот крем"))
        assertFalse(r.hasAd)
    }

    @Test
    fun twoSeparateIntegrations() {
        val captions = listOf(
            Caption(0, 5_000, "Этот выпуск спонсирует Ozon"),
            Caption(5_000, 9_000, "Промокод VIDEO даёт скидку"),
            Caption(10_000, 15_000, "Итак, вернёмся к обзору телефона"),
            Caption(15_000, 40_000, "Камера снимает отлично даже ночью"),
            Caption(40_000, 70_000, "Батареи хватает на два дня"),
            Caption(70_000, 75_000, "Кстати, переходи по ссылке в описании"),
            Caption(75_000, 80_000, "Там скидка на чехлы по моему коду"),
            Caption(80_000, 85_000, "Всем пока"),
        )
        val r = detector.detect(VideoMeta("8", captions = captions, durationMs = 85_000))
        assertEquals(2, r.segments.size)
        assertEquals(0, r.segments[0].startMs)
        assertEquals(10_000, r.segments[0].endMs)
        assertEquals(69_000, r.segments[1].startMs)
        assertTrue(r.segments[1].endMs in 80_000..85_000)
    }

    @Test
    fun englishSponsorRead() {
        val captions = vtt(
            """
            WEBVTT

            00:00:01.000 --> 00:00:04.000
            Today I'm testing the cheapest phone on the market

            00:00:05.000 --> 00:00:09.000
            <v Speaker>This video is sponsored by NordVPN</v>

            00:00:09.000 --> 00:00:13.000
            Use code TOK for 70% off, link in bio

            00:00:14.000 --> 00:00:17.000
            Anyway, back to the phone
            """
        )
        val r = detector.detect(VideoMeta("9", captions = captions))
        assertEquals(1, r.segments.size)
        assertEquals(4_000, r.segments[0].startMs)
        assertEquals(14_000, r.segments[0].endMs)
    }

    @Test
    fun userBrandAndPhraseAreUsed() {
        val captions = listOf(
            Caption(0, 5_000, "Вот мой рабочий стол"),
            Caption(5_000, 9_000, "Всё это я купил в Лавке Ромашка"),
            Caption(9_000, 13_000, "у них сейчас грандиозная распродажа"),
        )
        assertFalse(detector.detect(VideoMeta("10", captions = captions)).hasAd)
        val custom = AdDetector(DetectorConfig(brands = listOf("лавка ромашка"), phrases = listOf("грандиозная распродажа")))
        val r = custom.detect(VideoMeta("10", captions = captions))
        assertEquals(1, r.segments.size)
        assertEquals(4_000, r.segments[0].startMs)
    }

    @Test
    fun descriptionMarkerBoostsWeakCaptionCluster() {
        val captions = listOf(
            Caption(0, 4_000, "Смотрите что пришло"),
            Caption(4_000, 8_000, "Ссылка в профиле"),
            Caption(8_000, 12_000, "Очень удобно"),
        )
        val plain = AdDetector(DetectorConfig(threshold = 0.8f)).detect(VideoMeta("11", captions = captions))
        assertTrue(plain.segments.isEmpty())
        val marked = AdDetector(DetectorConfig(threshold = 0.8f))
            .detect(VideoMeta("11", description = "Реклама. ООО Ромашка ИНН 7700000000", captions = captions))
        assertEquals(1, marked.segments.size)
    }

    @Test
    fun customSignalPlugsIn() {
        val emoji = object : AdSignal {
            override val id = "emoji"
            override fun match(normalized: String) =
                if ("спецпредложение" in normalized) listOf(SignalHit(id, HitKind.TRIGGER, 0.9f, "спецпредложение")) else emptyList()
        }
        val r = AdDetector(extraSignals = listOf(emoji))
            .detect(VideoMeta("12", captions = listOf(Caption(3_000, 6_000, "Только сегодня спецпредложение"))))
        assertEquals(listOf(2_000L to 7_000L), r.segments.map { it.startMs to it.endMs })
    }
}
