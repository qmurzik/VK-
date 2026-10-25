package com.qmurzik.tokskip.core.model

/** One subtitle / on-screen text line with its time span inside the video. */
data class Caption(val startMs: Long, val endMs: Long, val text: String)

/** Everything the detector may know about a video. Missing parts are simply empty. */
data class VideoMeta(
    val videoId: String,
    val author: String = "",
    val description: String = "",
    /** Text drawn on top of the video without timing (stickers, title cards). */
    val onScreenText: List<String> = emptyList(),
    /** Subtitles / auto-captions / timed on-screen text. */
    val captions: List<Caption> = emptyList(),
    val durationMs: Long = 0L,
)

enum class SegmentSource { AUTO, MANUAL }

/** An advertising segment `[startMs, endMs)` with the detector's confidence 0..1. */
data class Segment(val startMs: Long, val endMs: Long, val confidence: Float = 1f) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
    operator fun contains(positionMs: Long) = positionMs in startMs until endMs
}

data class DetectionResult(
    /** The video advertises something (with or without known timings). */
    val hasAd: Boolean,
    /** Timed segments; empty when the ad is known only from the description. */
    val segments: List<Segment>,
    /** Overall confidence that the video contains an ad. */
    val confidence: Float,
    /** Human readable reasons, for the log screen. */
    val reasons: List<String> = emptyList(),
) {
    companion object {
        val NONE = DetectionResult(false, emptyList(), 0f)
    }
}
