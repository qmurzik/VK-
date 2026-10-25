package com.qmurzik.tokskip.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qmurzik.tokskip.core.TokSkip
import com.qmurzik.tokskip.core.data.VideoEntry
import com.qmurzik.tokskip.core.model.SegmentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DatabaseScreen(app: TokSkip, modifier: Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by app.db.videos().observeAll().collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<VideoEntry?>(null) }
    var creating by remember { mutableStateOf(false) }

    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(app.exportJson().toByteArray()) } }.isSuccess
            }
            Toast.makeText(ctx, if (ok) "Экспортировано: ${all.size}" else "Не удалось сохранить", Toast.LENGTH_SHORT).show()
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching {
                    val json = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    "Импортировано: ${app.importJson(json)}"
                }.getOrElse { "Ошибка импорта: ${it.message}" }
            }
            app.log.log("db", msg)
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    val shown = remember(all, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all else all.filter { q in it.videoId.lowercase() || q in it.author.lowercase() || q in it.description.lowercase() }
    }

    Column(modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("База сегментов", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ creating = true }) { Text("Добавить") }
            OutlinedButton({ export.launch("tokskip-segments.json") }) { Text("Экспорт") }
            OutlinedButton({ import.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("Импорт") }
        }
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Поиск: автор, описание, id") }, singleLine = true)
        if (shown.isEmpty()) Hint("Пока пусто. Разметьте ролик плавающими кнопками, добавьте вручную или импортируйте файл разметки.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.videoId }) { e -> EntryCard(e) { editing = e } }
        }
    }

    editing?.let { e ->
        EditDialog(
            title = "Ролик @${e.author.ifEmpty { "?" }}",
            initialId = e.videoId, idEditable = false,
            initialSegments = e.segments.joinToString("\n") { Format.segment(it) },
            initialHasAd = e.hasAd,
            onDismiss = { editing = null },
            onDelete = { scope.launch { app.db.videos().delete(e.videoId) }; editing = null },
        ) { _, segs, hasAd ->
            scope.launch {
                app.db.videos().upsert(e.copy(segments = segs, hasAd = hasAd || segs.isNotEmpty(), source = SegmentSource.MANUAL, updatedAt = System.currentTimeMillis()))
            }
            editing = null
        }
    }
    if (creating) {
        EditDialog(
            title = "Новая разметка", initialId = "", idEditable = true, initialSegments = "", initialHasAd = true,
            onDismiss = { creating = false }, onDelete = null,
        ) { id, segs, hasAd ->
            scope.launch {
                app.db.videos().upsert(VideoEntry(Format.videoIdFrom(id), "", "", segs, SegmentSource.MANUAL, hasAd || segs.isNotEmpty(), System.currentTimeMillis()))
            }
            creating = false
        }
    }
}

@Composable
private fun EntryCard(e: VideoEntry, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (e.author.isNotEmpty()) "@${e.author}" else e.videoId, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                AssistChip({}, { Text(if (e.source == SegmentSource.MANUAL) "вручную" else "авто") })
            }
            if (e.description.isNotEmpty()) Text(e.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Text(
                if (e.segments.isEmpty()) "Содержит рекламу, таймкоды неизвестны"
                else e.segments.joinToString { s -> Format.segment(s) + if (s.confidence < 1f) " (${(s.confidence * 100).toInt()}%)" else "" },
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EditDialog(
    title: String,
    initialId: String,
    idEditable: Boolean,
    initialSegments: String,
    initialHasAd: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, List<com.qmurzik.tokskip.core.model.Segment>, Boolean) -> Unit,
) {
    var id by remember { mutableStateOf(initialId) }
    var text by remember { mutableStateOf(initialSegments) }
    var hasAd by remember { mutableStateOf(initialHasAd) }
    val parsed = Format.parseSegments(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(id, { id = it }, enabled = idEditable, label = { Text("id ролика или ссылка") }, singleLine = true)
                OutlinedTextField(
                    text, { text = it }, label = { Text("Сегменты: м:сс-м:сс, по одному в строке") }, minLines = 3,
                    isError = parsed == null, supportingText = { if (parsed == null) Text("Пример: 0:12-0:27") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(hasAd, { hasAd = it }); Text("Содержит рекламу")
                }
            }
        },
        confirmButton = {
            TextButton({ onSave(id, parsed!!, hasAd) }, enabled = parsed != null && id.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onDelete) { Text("Удалить") }
                TextButton(onDismiss) { Text("Отмена") }
            }
        },
    )
}
