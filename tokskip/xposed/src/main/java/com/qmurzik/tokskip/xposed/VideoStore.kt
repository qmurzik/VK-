package com.qmurzik.tokskip.xposed

import com.qmurzik.tokskip.core.data.VideoKeys
import com.qmurzik.tokskip.core.detect.AdDetector
import com.qmurzik.tokskip.core.model.Caption
import com.qmurzik.tokskip.core.model.SegmentSource
import com.qmurzik.tokskip.core.model.Segment
import com.qmurzik.tokskip.core.model.VideoMeta
import com.qmurzik.tokskip.core.settings.Settings
import java.util.concurrent.ConcurrentHashMap

/** What the TikTok process knows about each video seen in the feed, keyed by aweme id. */
object VideoStore {
    class Known(val segments: List<Segment>, val hasAd: Boolean, val source: SegmentSource?)

    private val metas = ConcurrentHashMap<String, VideoMeta>()
    private val known = ConcurrentHashMap<String, Known>()
    private val sourceToAid = ConcurrentHashMap<String, String>()
    @Volatile private var detector: Pair<Settings, AdDetector>? = null

    fun meta(aid: String): VideoMeta? = metas[aid]
    fun known(aid: String): Known? = known[aid]

    /** A video parsed from the feed. Looks up stored markup and runs the detector ahead of playback. */
    fun onFeedItem(meta: VideoMeta, playSources: List<String>) {
        playSources.filter { it.length >= 6 }.forEach { sourceToAid[it] = meta.videoId }
        if (metas.putIfAbsent(meta.videoId, meta) != null) return
        if (metas.size > MAX) trim()
        Bridge.runInBackground { resolve(meta) }
    }

    /** Subtitles for [aid] became available (from the caption parser hook). */
    fun onCaptions(aid: String, captions: List<Caption>) {
        val meta = metas[aid]?.copy(captions = captions) ?: return
        metas[aid] = meta
        Bridge.runInBackground {
            if (known[aid]?.source == SegmentSource.MANUAL) return@runInBackground
            detect(meta)
        }
    }

    /** Finds the aweme id for a player data source (video id, url key or URL). */
    fun aidForSource(source: String?): String? {
        source ?: return null
        sourceToAid[source]?.let { return it }
        return sourceToAid.entries.firstOrNull { source.contains(it.key) }?.value
    }

    fun invalidate() {
        known.entries.removeIf { it.value.source != SegmentSource.MANUAL }
        metas.values.toList().forEach { m -> Bridge.runInBackground { resolve(m) } }
    }

    private fun aliases(meta: VideoMeta) = listOfNotNull(VideoKeys.uiKey(meta.author, meta.description))

    private fun resolve(meta: VideoMeta) {
        val stored = Bridge.lookup(listOf(meta.videoId) + aliases(meta))
        if (stored != null) {
            known[meta.videoId] = Known(stored.segments, stored.hasAd, stored.source)
            if (stored.hasAd) L.d { "в базе: ${meta.videoId} ${stored.source} ${stored.segments}" }
            if (stored.source == SegmentSource.MANUAL || meta.captions.isEmpty()) return
        }
        detect(meta)
    }

    private fun detect(meta: VideoMeta) {
        val s = Bridge.settings ?: Settings()
        if (!s.autoDetect) return
        val det = detector?.takeIf { it.first == s }?.second ?: AdDetector(s.detectorConfig()).also { detector = s to it }
        val result = det.detect(meta)
        if (!result.hasAd) return
        val prev = known[meta.videoId]
        if (prev?.source == SegmentSource.MANUAL) return
        if (prev != null && prev.segments == result.segments) return
        known[meta.videoId] = Known(result.segments, true, SegmentSource.AUTO)
        L.i("реклама в ${meta.videoId} (@${meta.author}): " +
            if (result.segments.isEmpty()) "без таймкодов" else result.segments.joinToString { "${it.startMs}–${it.endMs}" })
        Bridge.report(meta, result, aliases(meta))
    }

    private fun trim() {
        metas.keys.take(metas.size - MAX / 2).forEach { metas.remove(it); known.remove(it) }
    }

    private const val MAX = 400
}
