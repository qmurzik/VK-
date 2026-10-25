package com.qmurzik.tokskip.xposed

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.qmurzik.tokskip.core.Constants
import com.qmurzik.tokskip.core.data.BackupCodec
import com.qmurzik.tokskip.core.data.Converters
import com.qmurzik.tokskip.core.data.VideoEntry
import com.qmurzik.tokskip.core.model.DetectionResult
import com.qmurzik.tokskip.core.model.VideoMeta
import com.qmurzik.tokskip.core.settings.Settings
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.Executors

/**
 * The TikTok side of the link to the TokSkip app.
 *
 * Primary channel: the app's exported ContentProvider (settings, segment lookups, reports, stats,
 * logs). If TikTok cannot reach it (package visibility on some ROMs), the module falls back to the
 * libxposed remote preferences / remote file the app mirrors its settings and database into —
 * read-only, but enough to skip. All IPC runs on one background thread, never on TikTok's UI thread.
 */
object Bridge {
    private val uri: Uri = Uri.parse("content://${Constants.PROVIDER_AUTHORITY}")
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "TokSkip-bridge").apply { isDaemon = true } }
    private val pendingLogs = ArrayList<String>()

    @Volatile var settings: Settings? = null
        private set
    @Volatile private var context: Context? = null
    @Volatile private var providerOk = false
    @Volatile private var fallbackEntries: Map<String, VideoEntry> = emptyMap()
    private var xposed: XposedInterface? = null

    /** Called once the Application exists. */
    fun attach(ctx: Context, xposed: XposedInterface) {
        this.xposed = xposed
        context = ctx
        executor.execute {
            reloadConfig()
            synchronized(pendingLogs) { pendingLogs.toList().also { pendingLogs.clear() } }.forEach { call("log", it, null) }
        }
        runCatching {
            ctx.contentResolver.registerContentObserver(uri, true, object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) { executor.execute { reloadConfig(); VideoStore.invalidate() } }
            })
        }
    }

    private fun reloadConfig() {
        val b = call("config", null, null)
        providerOk = b != null
        val json = b?.getString("settings") ?: runCatching { xposed?.getRemotePreferences(REMOTE_GROUP)?.getString("settings_json", null) }.getOrNull()
        settings = json?.let { runCatching { Settings.fromJson(it) }.getOrNull() } ?: settings
        if (!providerOk) loadFallbackDb()
        L.verbose = settings?.verboseLog == true
    }

    private fun loadFallbackDb() {
        val x = xposed ?: return
        fallbackEntries = runCatching {
            x.openRemoteFile(REMOTE_DB).use { pfd ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
            }.let(BackupCodec::decode).associateBy { it.videoId }
        }.getOrDefault(fallbackEntries)
    }

    /** Blocking; call only from [executor]-like background threads. */
    fun lookup(ids: List<String>): VideoEntry? {
        if (providerOk) {
            val b = call("lookup", ids.joinToString("\n"), null) ?: return null
            val segs = b.getString("segments") ?: return null
            return VideoEntry(
                b.getString("videoId") ?: ids.first(), b.getString("author").orEmpty(), "",
                Converters.decodeSegments(segs), Converters().stringToSource(b.getString("source") ?: "AUTO"),
                b.getBoolean("hasAd"), 0
            )
        }
        return ids.firstNotNullOfOrNull { fallbackEntries[it] }
    }

    fun report(meta: VideoMeta, result: DetectionResult, aliases: List<String>) = async {
        call("report", null, Bundle().apply {
            putString("videoId", meta.videoId)
            putString("author", meta.author)
            putString("description", meta.description.take(500))
            putString("segments", Converters.encodeSegments(result.segments))
            putBoolean("hasAd", result.hasAd)
            putStringArrayList("aliases", ArrayList(aliases))
        })
    }

    fun skipped(ms: Long) = async { call("skip", ms.toString(), null) }

    fun ping(status: String) = async { call("ping", status, null) }

    fun log(msg: String) {
        if (context == null) { synchronized(pendingLogs) { if (pendingLogs.size < 200) pendingLogs += msg }; return }
        async { call("log", msg, null) }
    }

    fun async(block: () -> Unit) = runCatching { executor.execute { runCatching(block) } }

    fun runInBackground(block: () -> Unit) = async(block)

    private fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        return try {
            ctx.contentResolver.call(uri, method, arg, extras)
        } catch (t: Throwable) {
            android.util.Log.w(L.TAG, "provider call $method failed: $t")
            null
        }
    }

    const val REMOTE_GROUP = "tokskip"
    const val REMOTE_DB = "segments.json"
}
