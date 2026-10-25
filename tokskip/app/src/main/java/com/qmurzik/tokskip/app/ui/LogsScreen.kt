package com.qmurzik.tokskip.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qmurzik.tokskip.core.TokSkip

@Composable
fun LogsScreen(app: TokSkip, modifier: Modifier) {
    val lines by app.log.lines.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    Column(modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Логи", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({
                ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("TokSkip log", lines.joinToString("\n")))
                Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
            }) { Text("Копировать") }
            OutlinedButton({ app.log.clear() }) { Text("Очистить") }
        }
        Hint("Здесь пишутся найденные хуки, перемотки и ошибки. Лог хранится только на телефоне.")
        if (lines.isEmpty()) Text("Пока пусто")
        SelectionContainer {
            LazyColumn {
                items(lines.asReversed()) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(vertical = 3.dp))
                    HorizontalDivider()
                }
            }
        }
    }
}
