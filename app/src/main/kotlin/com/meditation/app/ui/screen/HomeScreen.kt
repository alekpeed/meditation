package com.meditation.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meditation.app.ui.Format
import com.meditation.app.ui.MeditationViewModel
import com.meditation.core.OvertimeMode
import com.meditation.core.SoundCategory

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: MeditationViewModel) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val sounds by vm.sounds.collectAsStateWithLifecycle()
    val totalMs by vm.totalActiveMs.collectAsStateWithLifecycle()
    val count by vm.sessionCount.collectAsStateWithLifecycle()

    var durationMs by remember { mutableLongStateOf(prefs.defaultDurationMs) }
    var withPrep by remember { mutableStateOf(false) }
    var withBells by remember { mutableStateOf(true) }
    var withAmbience by remember { mutableStateOf(false) }

    val openingBell = remember(sounds) { sounds.firstOrNull { it.category == SoundCategory.BELL } }
    val ambience = remember(sounds) { sounds.firstOrNull { it.category == SoundCategory.AMBIENCE } }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        Text("Meditation Timer", style = MaterialTheme.typography.headlineSmall)
        Text(
            "${count} sessions · ${Format.durationWords(totalMs)} total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Quick start", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5L, 10L, 15L, 20L, 30L).forEach { m ->
                        FilterChip(
                            selected = durationMs == m * 60_000,
                            onClick = { durationMs = m * 60_000 },
                            label = { Text("$m min") },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow("Preparation (10s)", withPrep) { withPrep = it }
                ToggleRow("Opening & closing bell", withBells) { withBells = it }
                ToggleRow("Ambient sound", withAmbience) { withAmbience = it }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        vm.startQuickSession(
                            durationMs = durationMs,
                            preparationMs = if (withPrep) 10_000 else 0,
                            openingSoundId = openingBell?.id?.takeIf { withBells },
                            intervalSoundId = null,
                            intervalEveryMs = null,
                            closingSoundId = openingBell?.id?.takeIf { withBells },
                            ambienceSoundId = ambience?.id?.takeIf { withAmbience },
                            overtimeMode = prefs.overtimeMode.takeIf { it != OvertimeMode.STOP } ?: OvertimeMode.STOP,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start ${Format.durationWords(durationMs)} session") }
            }
        }

        if (favorites.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Favorites", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            favorites.take(4).forEach { preset ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                Format.durationWords(preset.plannedDurationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { vm.start(preset) }) { Text("Start") }
                    }
                }
            }
        }

        history.firstOrNull()?.let { recent ->
            Spacer(Modifier.height(20.dp))
            Text("Recent", style = MaterialTheme.typography.titleMedium)
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(recent.presetName)
                    Text(
                        "${Format.dateTime(recent.startedWallMs)} · ${Format.durationWords(recent.actualActiveDurationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
