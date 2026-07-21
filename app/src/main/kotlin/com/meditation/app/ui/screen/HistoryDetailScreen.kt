package com.meditation.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.meditation.app.ui.Format
import com.meditation.app.ui.MeditationViewModel
import com.meditation.core.CompletionStatus

@Composable
fun HistoryDetailScreen(vm: MeditationViewModel, sessionId: String, onBack: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    val entry = remember(history, sessionId) { history.firstOrNull { it.sessionId == sessionId } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Session", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))

        if (entry == null) {
            Text("This session is no longer available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(entry.presetName, style = MaterialTheme.typography.titleLarge)
                Text(Format.dateTime(entry.startedWallMs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                DetailRow("Actual", Format.durationWords(entry.actualActiveDurationMs))
                DetailRow("Intended", Format.durationWords(entry.intendedDurationMs))
                if (entry.pausedDurationMs > 0) DetailRow("Paused", Format.durationWords(entry.pausedDurationMs))
                if (entry.overtimeDurationMs > 0) DetailRow("Overtime", Format.durationWords(entry.overtimeDurationMs))
                DetailRow(
                    "Status",
                    when (entry.completionStatus) {
                        CompletionStatus.COMPLETED -> "Completed"
                        CompletionStatus.FINISHED_EARLY -> "Finished early"
                        CompletionStatus.CANCELLED -> "Discarded"
                    },
                )
                entry.moodAfter?.let { DetailRow("Mood after", "$it / 5") }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Note & tags", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        var note by remember(entry.sessionId) { mutableStateOf(entry.note ?: "") }
        var tags by remember(entry.sessionId) { mutableStateOf(entry.tags.joinToString(", ")) }
        OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma-separated)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                vm.updateHistory(
                    entry.sessionId,
                    note.ifBlank { null },
                    tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    entry.moodAfter,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
