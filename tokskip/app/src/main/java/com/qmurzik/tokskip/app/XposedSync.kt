package com.qmurzik.tokskip.app

import android.content.Context
import com.qmurzik.tokskip.core.TokSkip
import com.qmurzik.tokskip.core.data.BackupCodec
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * Keeps the hook inside TikTok up to date:
 * - notifies the provider URI so the hook reloads settings and markup at once;
 * - when LSPosed's service is bound, mirrors settings into remote preferences and the segment
 *   database into a remote file — the fallback channel for ROMs where TikTok cannot reach the
 *   provider.
 */
object XposedSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var service: XposedService? = null
    private val _framework = MutableStateFlow<String?>(null)
    /** "LSPosed 1.x (API 102)" while the framework's service is bound. */
    val framework: StateFlow<String?> = _framework

    @OptIn(FlowPreview::class)
    fun start(context: Context, app: TokSkip) {
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(s: XposedService) {
                    service = s
                    _framework.value = "${s.frameworkName} ${s.frameworkVersion} (API ${s.apiVersion})"
                    scope.launch { push(app) }
                }

                override fun onServiceDied(s: XposedService) {
                    service = null
                    _framework.value = null
                }
            })
        }
        scope.launch {
            app.settings.settings.combine(app.db.videos().observeAll()) { s, v -> s to v }
                .debounce(500)
                .collect {
                    runCatching { context.contentResolver.notifyChange(TokSkipProvider.URI, null) }
                    push(app)
                }
        }
    }

    private suspend fun push(app: TokSkip) {
        val s = service ?: return
        runCatching {
            s.getRemotePreferences(REMOTE_GROUP).edit().putString("settings_json", app.settings.settings.value.toJson()).apply()
            val json = BackupCodec.encode(app.db.videos().all())
            s.openRemoteFile(REMOTE_DB).use { pfd ->
                android.os.ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                    out.channel.truncate(0)
                    out.write(json.toByteArray())
                }
            }
        }.onFailure { app.log.log("sync", "не удалось обновить данные LSPosed: $it") }
    }

    private const val REMOTE_GROUP = "tokskip"
    private const val REMOTE_DB = "segments.json"
}
