package com.qmurzik.tokskip.accessibility

import android.os.SystemClock

/**
 * Best-effort playback position for the rootless mode. Real positions (seek bar, time label) are
 * used whenever TikTok exposes them; between those, and for short videos without a seek bar, the
 * position is extrapolated from the time the video appeared. TikTok loops videos, so the estimate
 * wraps at the duration when it is known.
 */
class PlaybackClock(private val now: () -> Long = SystemClock::elapsedRealtime) {
    private var anchorPos = 0L
    private var anchorAt = now()
    var durationMs: Long = 0
    var exact = false
        private set

    private val lastSeen = HashMap<String, Long>()

    fun reset() {
        anchorPos = 0; anchorAt = now(); durationMs = 0; exact = false; lastSeen.clear()
    }

    /**
     * Anchors to a reading from [source] only when it changed: a seek bar that TikTok stops
     * updating must not freeze the clock at a stale value.
     */
    fun observeIfChanged(source: String, positionMs: Long) {
        if (lastSeen.put(source, positionMs) != positionMs) observe(positionMs)
    }

    fun observe(positionMs: Long) {
        anchorPos = positionMs; anchorAt = now(); exact = true
    }

    /** Position in ms and how many full loops have passed since the anchor. */
    fun position(): Long {
        val raw = anchorPos + (now() - anchorAt)
        return if (durationMs > 0) raw % durationMs else raw
    }

    fun loops(): Long = if (durationMs > 0) (anchorPos + (now() - anchorAt)) / durationMs else 0
}
