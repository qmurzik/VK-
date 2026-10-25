package com.qmurzik.tokskip.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qmurzik.tokskip.accessibility.TokSkipAccessibilityService
import com.qmurzik.tokskip.core.Constants
import kotlinx.coroutines.delay

object Env {
    fun a11yEnabled(ctx: Context): Boolean {
        val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return list.split(':').any {
            val c = ComponentName.unflattenFromString(it)
            c?.packageName == ctx.packageName && c.className == TokSkipAccessibilityService::class.java.name
        }
    }

    fun openA11ySettings(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun tiktokPackage(ctx: Context): String? = Constants.TIKTOK_PACKAGES.firstOrNull {
        ctx.packageManager.getLaunchIntentForPackage(it) != null
    }

    fun openTikTok(ctx: Context): Boolean {
        val intent = tiktokPackage(ctx)?.let { ctx.packageManager.getLaunchIntentForPackage(it) } ?: return false
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}

/** Re-evaluates [read] every second while on screen (permission states change outside the app). */
@Composable
fun <T> polled(initial: T, read: (Context) -> T): State<T> {
    val ctx = LocalContext.current
    return produceState(initial) {
        while (true) { value = read(ctx); delay(1000) }
    }
}

@Composable
fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
