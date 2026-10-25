package com.qmurzik.tokskip.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.qmurzik.tokskip.core.model.Segment
import com.qmurzik.tokskip.core.model.SegmentSource

/**
 * Known ad segments of one video. [videoId] is TikTok's aweme id when the root mode knows it, or a
 * `ui:` key derived from the visible description in the accessibility mode (see [VideoKeys]).
 */
@Entity(tableName = "videos")
data class VideoEntry(
    @PrimaryKey val videoId: String,
    val author: String,
    val description: String,
    val segments: List<Segment>,
    val source: SegmentSource,
    /** True when the video is known to contain an ad, even without timings. */
    val hasAd: Boolean,
    val updatedAt: Long,
)

class Converters {
    @TypeConverter
    fun segmentsToString(list: List<Segment>): String = encodeSegments(list)

    @TypeConverter
    fun stringToSegments(s: String): List<Segment> = decodeSegments(s)

    @TypeConverter
    fun sourceToString(s: SegmentSource): String = s.name

    @TypeConverter
    fun stringToSource(s: String): SegmentSource = runCatching { SegmentSource.valueOf(s) }.getOrDefault(SegmentSource.AUTO)

    companion object {
        /** Compact `start,end,confidence;…` encoding. */
        fun encodeSegments(list: List<Segment>) = list.joinToString(";") { "${it.startMs},${it.endMs},${it.confidence}" }

        fun decodeSegments(s: String): List<Segment> = s.split(';').mapNotNull { part ->
            val p = part.split(',')
            if (p.size < 2) null else runCatching {
                Segment(p[0].trim().toLong(), p[1].trim().toLong(), p.getOrNull(2)?.trim()?.toFloat() ?: 1f)
            }.getOrNull()
        }
    }
}
