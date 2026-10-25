package com.qmurzik.tokskip.core.data

import com.qmurzik.tokskip.core.model.Segment
import com.qmurzik.tokskip.core.model.SegmentSource
import org.json.JSONArray
import org.json.JSONObject

/** JSON export/import of the segment database, for sharing markup between users by hand. */
object BackupCodec {
    const val FORMAT = "tokskip-segments"
    const val VERSION = 1

    fun encode(entries: List<VideoEntry>): String {
        val arr = JSONArray()
        for (e in entries) arr.put(JSONObject().apply {
            put("videoId", e.videoId)
            put("author", e.author)
            put("description", e.description)
            put("source", e.source.name.lowercase())
            put("hasAd", e.hasAd)
            put("updatedAt", e.updatedAt)
            put("segments", JSONArray().apply {
                e.segments.forEach { s ->
                    put(JSONObject().put("startMs", s.startMs).put("endMs", s.endMs).put("confidence", s.confidence.toDouble()))
                }
            })
        })
        return JSONObject().put("format", FORMAT).put("version", VERSION).put("videos", arr).toString(2)
    }

    fun decode(json: String): List<VideoEntry> {
        val root = JSONObject(json)
        require(root.optString("format") == FORMAT) { "Это не файл разметки TokSkip" }
        val arr = root.getJSONArray("videos")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val segs = o.optJSONArray("segments") ?: JSONArray()
            VideoEntry(
                videoId = o.getString("videoId"),
                author = o.optString("author"),
                description = o.optString("description"),
                segments = (0 until segs.length()).map { j ->
                    val s = segs.getJSONObject(j)
                    Segment(s.getLong("startMs"), s.getLong("endMs"), s.optDouble("confidence", 1.0).toFloat())
                }.filter { it.endMs > it.startMs },
                source = if (o.optString("source") == "manual") SegmentSource.MANUAL else SegmentSource.AUTO,
                hasAd = o.optBoolean("hasAd", segs.length() > 0),
                updatedAt = o.optLong("updatedAt", 0L),
            )
        }
    }

    /** Import rule: manual markup beats auto detection; otherwise the newer entry wins. */
    fun pick(existing: VideoEntry?, incoming: VideoEntry): VideoEntry = when {
        existing == null -> incoming
        existing.source == SegmentSource.MANUAL && incoming.source == SegmentSource.AUTO -> existing
        incoming.source == SegmentSource.MANUAL && existing.source == SegmentSource.AUTO -> incoming
        incoming.updatedAt > existing.updatedAt -> incoming
        else -> existing
    }
}
