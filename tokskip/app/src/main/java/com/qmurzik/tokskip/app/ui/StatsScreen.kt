package com.qmurzik.tokskip.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qmurzik.tokskip.core.TokSkip
import com.qmurzik.tokskip.core.model.SegmentSource

@Composable
fun StatsScreen(app: TokSkip, modifier: Modifier) {
    val stats by app.settings.stats.collectAsStateWithLifecycle()
    val videos by app.db.videos().observeAll().collectAsStateWithLifecycle(emptyList())
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Статистика", style = MaterialTheme.typography.headlineMedium)
        Big("Пропущено роликов с рекламой", stats.skippedVideos.toString())
        Big("Сэкономлено времени", Format.duration(stats.skippedMs))
        Big("Найдено роликов с рекламой", stats.flaggedVideos.toString())
        Section("База") {
            Text("Всего роликов: ${videos.size}")
            Text("Ручная разметка: ${videos.count { it.source == SegmentSource.MANUAL }}")
            Text("Автоопределение: ${videos.count { it.source == SegmentSource.AUTO }}")
            Text("Без таймкодов: ${videos.count { it.hasAd && it.segments.isEmpty() }}")
        }
        OutlinedButton({ app.settings.resetStats() }) { Text("Сбросить счётчики") }
    }
}

@Composable
private fun Big(title: String, value: String) = Section(title) {
    Text(value, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
}
