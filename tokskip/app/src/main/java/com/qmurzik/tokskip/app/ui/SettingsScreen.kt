package com.qmurzik.tokskip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qmurzik.tokskip.core.TokSkip
import com.qmurzik.tokskip.core.detect.DetectorConfig
import com.qmurzik.tokskip.core.settings.FallbackAction
import com.qmurzik.tokskip.core.settings.Mode
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(app: TokSkip, modifier: Modifier) {
    val s by app.settings.settings.collectAsStateWithLifecycle()
    val update = app.settings::update

    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)

        Section("Режим") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(Mode.ACCESSIBILITY to "Без root", Mode.ROOT to "Root / LSPosed").forEachIndexed { i, (m, title) ->
                    SegmentedButton(
                        selected = s.mode == m,
                        onClick = { update { it.copy(mode = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(title) }
                }
            }
            Hint("В root-режиме перематывает модуль внутри TikTok; служба доступности только показывает кнопки разметки.")
        }

        Section("Действия при рекламе") {
            Toggle("Перематывать известные сегменты", s.skipSegments) { v -> update { it.copy(skipSegments = v) } }
            ActionPicker("Если перемотать нельзя", s.fallbackAction) { a -> update { it.copy(fallbackAction = a) } }
            ActionPicker("Реклама без таймкодов", s.unknownSegmentAction) { a -> update { it.copy(unknownSegmentAction = a) } }
            Hint("«Перелистнуть» работает в режиме без root; в root-режиме вместо него показывается уведомление.")
        }

        Section("Детектор") {
            Toggle("Автоопределение рекламы", s.autoDetect) { v -> update { it.copy(autoDetect = v) } }
            Toggle("Анализ экрана (OCR)", s.screenOcr) { v -> update { it.copy(screenOcr = v) } }
            Hint("Раз в секунду распознаёт текст внутри видео — вшитые субтитры и рекламные баннеры — и пропускает рекламу прямо во время просмотра. Работает на телефоне, Android 11+, режим без root.")
            Text("Порог уверенности: ${(s.threshold * 100).roundToInt()}%")
            Slider(s.threshold, { v -> update { it.copy(threshold = (v * 20).roundToInt() / 20f) } }, valueRange = 0.3f..0.95f)
            Hint("Ниже — больше находок и ложных срабатываний, выше — только явные интеграции.")
            ListEditor("Бренды (по одному в строке)", s.brands) { l -> update { it.copy(brands = l) } }
            Row { androidx.compose.material3.TextButton({ update { it.copy(brands = DetectorConfig.DEFAULT_BRANDS) } }) { Text("Вернуть бренды по умолчанию") } }
            ListEditor("Свои фразы-триггеры", s.phrases) { l -> update { it.copy(phrases = l) } }
            ListEditor("Белый список авторов (@ник)", s.whitelistAuthors) { l -> update { it.copy(whitelistAuthors = l) } }
        }

        Section("Режим без root: элементы экрана") {
            Hint("Если TikTok обновился и описание/субтитры не читаются — сделайте «Дамп UI» и впишите id элементов (часть после «:id/»).")
            Field("id описания", s.idDescription) { v -> update { it.copy(idDescription = v.trim()) } }
            Field("id субтитров", s.idCaption) { v -> update { it.copy(idCaption = v.trim()) } }
            Field("id автора", s.idAuthor) { v -> update { it.copy(idAuthor = v.trim()) } }
        }

        Section("Прочее") {
            Toggle("Плавающие кнопки разметки", s.showOverlay) { v -> update { it.copy(showOverlay = v) } }
            Toggle("Подробный лог", s.verboseLog) { v -> update { it.copy(verboseLog = v) } }
        }
    }
}

@Composable
private fun Toggle(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(value, onChange)
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true)
}

/** Multi-line list editor; keeps the raw text while typing and stores trimmed non-empty lines. */
@Composable
private fun ListEditor(label: String, items: List<String>, onChange: (List<String>) -> Unit) {
    var text by remember(label) { mutableStateOf(items.joinToString("\n")) }
    OutlinedTextField(
        value = text,
        onValueChange = { t -> text = t; onChange(t.lines().map { it.trim() }.filter { it.isNotEmpty() }) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = 2,
        maxLines = 8,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionPicker(label: String, value: FallbackAction, onChange: (FallbackAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }) {
        OutlinedTextField(
            value = value.title, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(open, { open = false }) {
            FallbackAction.entries.forEach { a ->
                DropdownMenuItem({ Text(a.title) }, { onChange(a); open = false })
            }
        }
    }
}
