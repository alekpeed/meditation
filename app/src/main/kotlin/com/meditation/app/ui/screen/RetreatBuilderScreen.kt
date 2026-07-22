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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meditation.app.ui.Format
import com.meditation.app.ui.MeditationViewModel
import com.meditation.core.SessionTemplates
import java.util.UUID

/**
 * A configurable version of the retreat template (brief §16 retreat builder): pick the number of
 * sittings and how long each sitting/rest is, see the total length update live, then save. Sound
 * choices come from [SessionTemplates.retreat]'s sensible defaults; fine per-stage edits (custom
 * sounds, interval bells) remain available afterward via the normal preset editor.
 */
@Composable
fun RetreatBuilderScreen(vm: MeditationViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("Half-Day Retreat") }
    var sittingsText by remember { mutableStateOf("4") }
    var sittingMinutesText by remember { mutableStateOf("25") }
    var restMinutesText by remember { mutableStateOf("5") }

    val sittings = (sittingsText.toIntOrNull() ?: 1).coerceIn(1, 12)
    val sittingMinutes = (sittingMinutesText.toIntOrNull() ?: 1).coerceIn(1, 180)
    val restMinutes = (restMinutesText.toIntOrNull() ?: 0).coerceIn(0, 60)

    val preview = remember(sittings, sittingMinutes, restMinutes) {
        SessionTemplates.retreat("preview", nowMs = 0, sittings = sittings, sittingMin = sittingMinutes, restMin = restMinutes)
    }

    fun buildPreset() = SessionTemplates.retreat(
        id = "preset-${UUID.randomUUID()}",
        nowMs = System.currentTimeMillis(),
        sittings = sittings,
        sittingMin = sittingMinutes,
        restMin = restMinutes,
    ).let { if (name.isNotBlank()) it.copy(name = name.trim()) else it }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Retreat builder", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    sittingsText, { sittingsText = it.filter(Char::isDigit) },
                    label = { Text("Number of sittings") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    sittingMinutesText, { sittingMinutesText = it.filter(Char::isDigit) },
                    label = { Text("Minutes per sitting") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    restMinutesText, { restMinutesText = it.filter(Char::isDigit) },
                    label = { Text("Minutes of rest between sittings") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Preview", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${preview.stages.size} stages · ${Format.durationWords(preview.plannedDurationMs)} total",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                preview.stages.forEach { stage ->
                    Text(
                        "• ${stage.name} — ${Format.durationWords(stage.durationMs ?: 0)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.savePreset(buildPreset()); onDone() },
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
            Button(
                onClick = { val p = buildPreset(); vm.savePreset(p); vm.start(p); onDone() },
                modifier = Modifier.weight(1f),
            ) { Text("Save & start") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
