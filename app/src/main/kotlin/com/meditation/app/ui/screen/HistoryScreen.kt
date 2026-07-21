package com.meditation.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meditation.app.ui.Format
import com.meditation.app.ui.MeditationViewModel
import com.meditation.core.CompletedSession
import com.meditation.core.CompletionStatus

@Composable
fun HistoryScreen(vm: MeditationViewModel, onOpen: (String) -> Unit = {}) {
    val history by vm.history.collectAsStateWithLifecycle()
    val count by vm.sessionCount.collectAsStateWithLifecycle()
    val totalMs by vm.totalActiveMs.collectAsStateWithLifecycle()

    if (history.isEmpty()) {
        EmptyState(
            title = "No sessions yet",
            body = "Your completed meditations will appear here with duration and notes.",
            actionLabel = null,
            onAction = null,
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("History", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Sessions", count.toString(), Modifier.weight(1f))
            StatTile("Total time", Format.durationWords(totalMs), Modifier.weight(1f))
            StatTile("Average", Format.durationWords(if (count > 0) totalMs / count else 0), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        MonthHeatmap(history)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(history, key = { it.sessionId }) { entry ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(entry.sessionId) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.presetName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                Format.dateTime(entry.startedWallMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val status = when (entry.completionStatus) {
                                CompletionStatus.COMPLETED -> "Completed"
                                CompletionStatus.FINISHED_EARLY -> "Finished early"
                                CompletionStatus.CANCELLED -> "Discarded"
                            }
                            Text(
                                "${Format.durationWords(entry.actualActiveDurationMs)} · $status",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { vm.deleteHistory(entry.sessionId) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A month grid coloring each day by total meditation minutes (backlog Phase 2 heatmap). */
@Composable
private fun MonthHeatmap(history: List<CompletedSession>) {
    val primary = MaterialTheme.colorScheme.primary
    val computed = remember(history) {
        val tz = java.util.TimeZone.getDefault()
        val nowCal = java.util.Calendar.getInstance(tz)
        val year = nowCal.get(java.util.Calendar.YEAR)
        val month = nowCal.get(java.util.Calendar.MONTH)
        val monthStart = java.util.Calendar.getInstance(tz).apply {
            set(year, month, 1, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        val daysInMonth = monthStart.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        val firstDow = monthStart.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
        val minutesByDay = IntArray(daysInMonth + 1)
        history.filter { it.completionStatus != CompletionStatus.CANCELLED }.forEach { s ->
            val c = java.util.Calendar.getInstance(tz).apply { timeInMillis = s.startedWallMs }
            if (c.get(java.util.Calendar.YEAR) == year && c.get(java.util.Calendar.MONTH) == month) {
                val d = c.get(java.util.Calendar.DAY_OF_MONTH)
                minutesByDay[d] += (s.actualActiveDurationMs / 60_000).toInt()
            }
        }
        Triple(daysInMonth, firstDow, minutesByDay)
    }
    val (daysInMonth, firstDow, minutesByDay) = computed
    val cells = buildList { repeat(firstDow) { add(0) }; for (d in 1..daysInMonth) add(d) }

    Column {
        Text("This month", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        var i = 0
        while (i < cells.size) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0 until 7) {
                    val idx = i + col
                    val day = cells.getOrNull(idx) ?: -1
                    val mins = if (day in 1..daysInMonth) minutesByDay[day] else 0
                    val alpha = when {
                        day <= 0 -> 0f
                        mins == 0 -> 0.08f
                        mins < 10 -> 0.3f
                        mins < 20 -> 0.55f
                        mins < 40 -> 0.8f
                        else -> 1f
                    }
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (day <= 0) Color.Transparent else primary.copy(alpha = alpha)),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            i += 7
        }
    }
}
