package com.qmurzik.tokskip.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qmurzik.tokskip.app.XposedSync
import com.qmurzik.tokskip.core.TokSkip
import com.qmurzik.tokskip.core.settings.Mode
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(app: TokSkip, modifier: Modifier, openDump: () -> Unit) {
    val ctx = LocalContext.current
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val stats by app.settings.stats.collectAsStateWithLifecycle()
    val a11y by polled(false) { Env.a11yEnabled(it) }
    val framework by XposedSync.framework.collectAsStateWithLifecycle()
    val tiktok by polled<String?>(null) { Env.tiktokPackage(it) }

    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("TokSkip", style = MaterialTheme.typography.headlineMedium)
        Section("Состояние") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Пропуск рекламы включён", Modifier.weight(1f))
                Switch(settings.enabled, { v -> app.settings.update { it.copy(enabled = v) } })
            }
            Text("Режим: " + if (settings.mode == Mode.ROOT) "root (LSPosed)" else "без root (служба доступности)")
            Text("TikTok: " + (tiktok ?: "не установлен"))
            Text("Служба доступности: " + if (a11y) "включена ✅" else "выключена ⛔")
            if (settings.mode == Mode.ROOT) {
                Text("LSPosed: " + (framework ?: "не подключён"))
                val ping = app.settings.lastHookPing
                Text("Модуль в TikTok: " + if (ping > 0) "активен с ${DateFormat.getDateTimeInstance().format(Date(ping))}" else "ещё не отмечался")
                if (app.settings.hookStatus.isNotEmpty()) Hint("Хуки — ${app.settings.hookStatus}")
            }
            Text("Пропущено: ${stats.skippedVideos} роликов, ${Format.duration(stats.skippedMs)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!a11y) OutlinedButton({ Env.openA11ySettings(ctx) }) { Text("Включить службу") }
                Button({ if (!Env.openTikTok(ctx)) Toast.makeText(ctx, "TikTok не найден", Toast.LENGTH_SHORT).show() }) { Text("Открыть TikTok") }
            }
        }
        Section("Ручная разметка") {
            Hint("Включите плавающие кнопки, откройте ролик и нажмите «Начало рекламы», а затем «Конец рекламы». " +
                "Таймкоды сохранятся в базу для этого ролика и будут пропускаться в обоих режимах.")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Плавающие кнопки поверх TikTok", Modifier.weight(1f))
                Switch(settings.showOverlay, { v -> app.settings.update { it.copy(showOverlay = v) } }, enabled = a11y)
            }
        }
        Section("Инструменты исследования") {
            Hint("«Дамп UI» сохраняет дерево элементов текущего экрана TikTok в файл: по нему видно id описания, субтитров и полосы прогресса.")
            OutlinedButton(openDump, Modifier.fillMaxWidth()) { Text("Дамп UI") }
            OutlinedButton({ app.settings.update { it.copy(onboardingDone = false) } }, Modifier.fillMaxWidth()) { Text("Пройти настройку заново") }
        }
    }
}
