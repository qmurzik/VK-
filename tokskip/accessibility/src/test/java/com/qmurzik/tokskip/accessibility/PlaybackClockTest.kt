package com.qmurzik.tokskip.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackClockTest {
    private var t = 0L
    private val clock = PlaybackClock { t }

    @Test
    fun extrapolatesAndWrapsAtDuration() {
        clock.reset(); clock.durationMs = 10_000
        t = 4_000; assertEquals(4_000, clock.position())
        t = 12_500; assertEquals(2_500, clock.position()); assertEquals(1, clock.loops())
    }

    @Test
    fun staleReadingDoesNotFreezeClock() {
        clock.reset()
        clock.observeIfChanged("bar", 1_000)
        t = 3_000; clock.observeIfChanged("bar", 1_000)
        assertEquals(4_000, clock.position())
        clock.observeIfChanged("bar", 9_000)
        assertEquals(9_000, clock.position())
    }
}
