package com.qmurzik.tokskip.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qmurzik.tokskip.accessibility.TokSkipAccessibilityService
import com.qmurzik.tokskip.accessibility.UiDumper
import kotlinx.coroutines.delay
import java.io.File

/** "Дамп UI": saves the accessibility tree of the TikTok screen to a file and shows saved dumps. */
@Composable
fun DumpScreen(modifier: Modifier, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val files = remember(refresh) { UiDumper.dir(ctx).listFiles().orEmpty().sortedByDescending { it.lastModified() } }
    var open by remember { mutableStateOf<File?>(null) }
    var exporting by remember { mutableStateOf<File?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val f = exporting ?: return@rememberLauncherForActivityResult
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(f.readBytes()) }
            Toast.makeText(ctx, "Сохранено", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(2000); refresh++ } }

    Column(modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { TextButton(onBack) { Text("← Назад") } }
        Text("Дамп UI", style = MaterialTheme.typography.headlineMedium)
        Hint("Нажмите кнопку — откроется TikTok, через 5 секунд дерево экрана сохранится в файл. " +
            "Сделайте дамп с открытым роликом (лучше длинным, с полосой прогресса и включёнными субтитрами). " +
            "Кнопка «Дамп UI» есть и в плавающей панели разметки.")
        Button({
            val svc = TokSkipAccessibilityService.instance
            if (svc == null) {
                Toast.makeText(ctx, "Сначала включите службу доступности TokSkip", Toast.LENGTH_LONG).show()
                Env.openA11ySettings(ctx)
            } else {
                svc.requestDump(5_000)
                Env.openTikTok(ctx)
            }
        }, Modifier.fillMaxWidth()) { Text("Сделать дамп через 5 с") }
        Text("Файлы (${files.size})", style = MaterialTheme.typography.titleMedium)
        LazyColumn {
            items(files, key = { it.name }) { f ->
                ListItem(
                    headlineContent = { Text(f.name) },
                    supportingContent = { Text("${f.length() / 1024} КБ") },
                    trailingContent = {
                        Row {
                            TextButton({ open = f }) { Text("Открыть") }
                            TextButton({ exporting = f; save.launch(f.name) }) { Text("Сохранить") }
                            TextButton({ f.delete(); refresh++ }) { Text("✕") }
                        }
                    },
                )
            }
        }
    }

    open?.let { f ->
        AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = { TextButton({ open = null }) { Text("Закрыть") } },
            title = { Text(f.name) },
            text = {
                val text = remember(f) { runCatching { f.readText() }.getOrDefault("") }
                Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            },
        )
    }
}
