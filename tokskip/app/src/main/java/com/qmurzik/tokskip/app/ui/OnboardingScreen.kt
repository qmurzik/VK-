package com.qmurzik.tokskip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qmurzik.tokskip.app.XposedSync
import com.qmurzik.tokskip.core.TokSkip
import com.qmurzik.tokskip.core.settings.Mode

@Composable
fun OnboardingScreen(app: TokSkip) {
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    val ctx = LocalContext.current

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("TokSkip", style = MaterialTheme.typography.displaySmall)
            when (step) {
                0 -> {
                    Text("Пропускает рекламные интеграции внутри роликов TikTok — как SponsorBlock.", style = MaterialTheme.typography.titleMedium)
                    Hint("Всё работает на телефоне: без сети, без сбора данных, без действий с вашим аккаунтом. Видео не скачиваются.")
                    Text("Выберите режим", style = MaterialTheme.typography.titleMedium)
                    ModeCard(
                        selected = settings.mode == Mode.ACCESSIBILITY,
                        title = "Без root (служба доступности)",
                        text = "Читает описание и субтитры с экрана, перематывает жестом по полосе прогресса. " +
                            "Если перемотать нельзя — уведомление или перелистывание.",
                    ) { app.settings.update { it.copy(mode = Mode.ACCESSIBILITY) } }
                    ModeCard(
                        selected = settings.mode == Mode.ROOT,
                        title = "Root (модуль LSPosed)",
                        text = "Анализирует ленту заранее и перематывает точно через плеер TikTok. Нужны root и LSPosed.",
                    ) { app.settings.update { it.copy(mode = Mode.ROOT) } }
                    Button({ step = 1 }, Modifier.fillMaxWidth()) { Text("Далее") }
                }
                else -> {
                    val a11y by polled(false) { Env.a11yEnabled(it) }
                    val framework by XposedSync.framework.collectAsStateWithLifecycle()
                    if (settings.mode == Mode.ACCESSIBILITY) {
                        Text("Включите службу доступности", style = MaterialTheme.typography.titleMedium)
                        Hint("Настройки → Спец. возможности → Установленные службы → «TokSkip — пропуск рекламы». " +
                            "Служба получает события только от TikTok.")
                        Text(if (a11y) "✅ Служба включена" else "⛔ Служба выключена")
                        OutlinedButton({ Env.openA11ySettings(ctx) }, Modifier.fillMaxWidth()) { Text("Открыть настройки доступности") }
                        Hint("Служба также нужна в root-режиме для плавающих кнопок ручной разметки и дампа UI.")
                    } else {
                        Text("Включите модуль в LSPosed", style = MaterialTheme.typography.titleMedium)
                        Hint("1. Откройте LSPosed → Модули → TokSkip → включите.\n2. Отметьте TikTok в области действия (обычно уже отмечен).\n" +
                            "3. Принудительно остановите TikTok и откройте снова.")
                        Text(if (framework != null) "✅ Связь с LSPosed: $framework" else "⏳ LSPosed пока не подключился к приложению")
                        Text(if (app.settings.lastHookPing > 0) "✅ Модуль отметился в TikTok" else "⏳ Модуль ещё не запускался в TikTok")
                        Hint("Если хуки не найдутся в вашей версии TikTok, модуль просто отключит функцию и запишет причину в «Логи» — TikTok не упадёт.")
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ step = 0 }) { Text("Назад") }
                        Spacer(Modifier.weight(1f))
                        Button({ app.settings.update { it.copy(onboardingDone = true) } }) { Text("Готово") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(selected: Boolean, title: String, text: String, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected, onClick)
            Column(Modifier.padding(start = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Hint(text)
            }
        }
    }
}
