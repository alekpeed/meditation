package com.meditation.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meditation.app.ui.Format
import com.meditation.app.ui.MeditationViewModel
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
