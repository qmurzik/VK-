package com.qmurzik.tokskip.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.qmurzik.tokskip.core.Constants
import com.qmurzik.tokskip.core.TokSkip
import com.qmurzik.tokskip.core.data.Converters
import com.qmurzik.tokskip.core.model.DetectionResult
import com.qmurzik.tokskip.core.model.VideoMeta
import kotlinx.coroutines.runBlocking

/**
 * IPC endpoint for the LSPosed hook running inside TikTok (see `:xposed` Bridge).
 *
 * Read methods (config, lookup) serve non-sensitive data: settings and ad timings. Methods that
 * write are accepted only from the TikTok packages and from this app itself.
 */
class TokSkipProvider : ContentProvider() {
    private val app by lazy { TokSkip.get(context!!) }

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = when (method) {
        "config" -> Bundle().apply { putString("settings", app.settings.settings.value.toJson()) }
        "lookup" -> lookup(arg.orEmpty().split('\n').filter { it.isNotBlank() })
        "report" -> trusted { report(extras ?: return@trusted) }
        "skip" -> trusted { app.settings.addSkip(arg?.toLongOrNull() ?: 0) }
        "log" -> trusted { app.log.log("root", arg.orEmpty().take(2000)) }
        "ping" -> trusted {
            app.settings.lastHookPing = System.currentTimeMillis()
            app.settings.hookStatus = arg.orEmpty()
            app.log.log("root", "модуль активен в TikTok: $arg")
        }
        else -> null
    }

    private fun lookup(ids: List<String>): Bundle? {
        if (ids.isEmpty()) return null
        val e = runBlocking { app.find(ids) } ?: return null
        return Bundle().apply {
            putString("videoId", e.videoId)
            putString("author", e.author)
            putString("segments", Converters.encodeSegments(e.segments))
            putString("source", e.source.name)
            putBoolean("hasAd", e.hasAd)
        }
    }

    private fun report(b: Bundle) {
        val meta = VideoMeta(b.getString("videoId") ?: return, b.getString("author").orEmpty(), b.getString("description").orEmpty())
        val segs = Converters.decodeSegments(b.getString("segments").orEmpty())
        val result = DetectionResult(b.getBoolean("hasAd"), segs, segs.maxOfOrNull { it.confidence } ?: 1f)
        runBlocking { app.saveAuto(meta, result, b.getStringArrayList("aliases").orEmpty()) }
    }

    private inline fun trusted(block: () -> Unit): Bundle? {
        val uid = Binder.getCallingUid()
        val pkgs = context!!.packageManager.getPackagesForUid(uid).orEmpty()
        if (uid != Process.myUid() && pkgs.none { it in Constants.TIKTOK_PACKAGES }) return null
        block()
        return Bundle.EMPTY
    }

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0

    companion object {
        val URI: Uri = Uri.parse("content://${Constants.PROVIDER_AUTHORITY}")
    }
}
