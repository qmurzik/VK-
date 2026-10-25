package com.qmurzik.tokskip.core

import android.content.Context
import com.qmurzik.tokskip.core.data.BackupCodec
import com.qmurzik.tokskip.core.data.TokSkipDb
import com.qmurzik.tokskip.core.data.VideoEntry
import com.qmurzik.tokskip.core.detect.AdDetector
import com.qmurzik.tokskip.core.log.LogStore
import com.qmurzik.tokskip.core.model.DetectionResult
import com.qmurzik.tokskip.core.model.Segment
import com.qmurzik.tokskip.core.model.SegmentSource
import com.qmurzik.tokskip.core.model.VideoMeta
import com.qmurzik.tokskip.core.settings.SettingsStore

object Constants {
    val TIKTOK_PACKAGES = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    const val PROVIDER_AUTHORITY = "com.qmurzik.tokskip.provider"
}

/** Process-wide entry point of the settings app: database, settings, log and the detector. */
class TokSkip private constructor(context: Context) {
    val db = TokSkipDb.create(context)
    val settings = SettingsStore(context)
    val log = LogStore(context)

    @Volatile private var detectorFor: Any? = null
    @Volatile private var cachedDetector: AdDetector? = null

    /** The detector for the current settings, rebuilt only when they change. */
    fun detector(): AdDetector {
        val cfg = settings.settings.value.detectorConfig()
        cachedDetector?.takeIf { detectorFor == cfg }?.let { return it }
        return AdDetector(cfg).also { cachedDetector = it; detectorFor = cfg }
    }

    /** First stored entry among [ids] (aweme id first, then `ui:` key). */
    suspend fun find(ids: List<String>): VideoEntry? {
        val found = db.videos().byIds(ids).associateBy { it.videoId }
        return ids.firstNotNullOfOrNull { found[it] }
    }

    /** Stores an automatic detection unless the user already marked the video by hand. */
    suspend fun saveAuto(meta: VideoMeta, result: DetectionResult, aliases: List<String> = emptyList()) {
        if (!result.hasAd) return
        val existing = find(listOf(meta.videoId) + aliases)
        if (existing?.source == SegmentSource.MANUAL) return
        if (existing != null && existing.segments == result.segments) return
        db.videos().upsert(
            VideoEntry(meta.videoId, meta.author, meta.description.take(500), result.segments, SegmentSource.AUTO, true, System.currentTimeMillis())
        )
        if (existing == null) settings.addFlagged()
    }

    /** Adds one manual segment to the video's markup. */
    suspend fun addManualSegment(videoId: String, author: String, description: String, segment: Segment) {
        val existing = db.videos().byId(videoId)
        val base = existing?.takeIf { it.source == SegmentSource.MANUAL }?.segments.orEmpty()
        val merged = (base + segment).sortedBy { it.startMs }
        db.videos().upsert(
            VideoEntry(
                videoId, author.ifBlank { existing?.author.orEmpty() }, description.ifBlank { existing?.description.orEmpty() }.take(500),
                merged, SegmentSource.MANUAL, true, System.currentTimeMillis()
            )
        )
    }

    suspend fun exportJson(): String = BackupCodec.encode(db.videos().all())

    /** Merges a shared JSON file; returns how many entries were added or replaced. */
    suspend fun importJson(json: String): Int {
        val incoming = BackupCodec.decode(json)
        val existing = db.videos().byIds(incoming.map { it.videoId }).associateBy { it.videoId }
        val changed = incoming.mapNotNull { e ->
            val pick = BackupCodec.pick(existing[e.videoId], e)
            pick.takeIf { it !== existing[e.videoId] }
        }
        db.videos().upsertAll(changed)
        return changed.size
    }

    companion object {
        @Volatile private var instance: TokSkip? = null

        fun get(context: Context): TokSkip = instance ?: synchronized(this) {
            instance ?: TokSkip(context.applicationContext).also { instance = it }
        }
    }
}
