package com.meditation.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.meditation.core.AmbienceLayer
import com.meditation.core.IntervalPlan
import com.meditation.core.SessionStage
import com.meditation.core.SoundAsset
import com.meditation.core.SoundRole

/**
 * Edits one stage: name, duration or open-ended (last stage only), opening/closing sounds, a fixed
 * interval-bell plan, and up to three ambience layers with a live mix preview (brief §12).
 */
@Composable
fun StageEditorDialog(
    stage: SessionStage,
    isLast: Boolean,
    sounds: List<SoundAsset>,
    onPreviewMix: (List<Pair<String, Double>>) -> Unit,
    onStopPreview: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (SessionStage) -> Unit,
) {
    var name by remember { mutableStateOf(stage.name) }
    var openEnded by remember { mutableStateOf(stage.durationMs == null) }
    var minutes by remember { mutableStateOf(((stage.durationMs ?: 300_000) / 60_000).toString()) }
    var openingId by remember { mutableStateOf(stage.openingSoundId) }
    var closingId by remember { mutableStateOf(stage.closingSoundId) }

    val existingInterval = stage.intervalPlan as? IntervalPlan.EveryXMinutes
    var intervalOn by remember { mutableStateOf(existingInterval != null) }
    var intervalMinutes by remember { mutableStateOf(((existingInterval?.intervalMs ?: 300_000) / 60_000).toString()) }
    var intervalSoundId by remember { mutableStateOf(existingInterval?.soundId) }

    val layers: SnapshotStateList<AmbienceLayer> = remember { stage.ambienceLayers.toMutableStateList() }

    fun currentLayers() = layers.map { it.soundId to it.volume }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Edit stage") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("Stage name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))

                if (isLast) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Open-ended (count up)")
                        Switch(checked = openEnded, onCheckedChange = { openEnded = it })
                    }
                }
                if (!openEnded) {
                    OutlinedTextField(
                        minutes, { minutes = it.filter(Char::isDigit) },
                        label = { Text("Duration (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))

                SoundPicker("Opening sound", SoundRole.OPENING, sounds, openingId, { openingId = it })
                Spacer(Modifier.height(8.dp))
                SoundPicker("Closing / transition sound", SoundRole.CLOSING, sounds, closingId, { closingId = it })
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Interval bell")
                    Switch(checked = intervalOn, onCheckedChange = { intervalOn = it })
                }
                if (intervalOn) {
                    OutlinedTextField(
                        intervalMinutes, { intervalMinutes = it.filter(Char::isDigit) },
                        label = { Text("Every (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    SoundPicker("Interval sound", SoundRole.INTERVAL, sounds, intervalSoundId, { intervalSoundId = it })
                }
                Spacer(Modifier.height(12.dp))

                Text("Ambience layers (up to 3)", style = MaterialTheme.typography.titleSmall)
                layers.forEachIndexed { i, layer ->
                    SoundPicker(
                        "Layer ${i + 1}", SoundRole.AMBIENCE, sounds, layer.soundId,
                        { id -> if (id != null) layers[i] = layer.copy(soundId = id) },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = layer.volume.toFloat(),
                            onValueChange = { layers[i] = layer.copy(volume = it.toDouble()) },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { layers.removeAt(i) }) { Icon(Icons.Filled.Delete, "Remove layer") }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (layers.size < 3) {
                        OutlinedButton(onClick = {
                            val firstAmbience = sounds.firstOrNull { SoundRole.AMBIENCE in it.roles }
                            if (firstAmbience != null) layers.add(AmbienceLayer(firstAmbience.id, 0.6))
                        }) { Text("Add layer") }
                    }
                    if (layers.isNotEmpty()) {
                        OutlinedButton(onClick = { onPreviewMix(currentLayers()) }) { Text("Preview") }
                        OutlinedButton(onClick = onStopPreview) { Text("Stop") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val duration = if (openEnded) null else (minutes.toLongOrNull() ?: 5) * 60_000
                val plan = if (intervalOn && intervalSoundId != null)
                    IntervalPlan.EveryXMinutes((intervalMinutes.toLongOrNull() ?: 5) * 60_000, intervalSoundId!!)
                else IntervalPlan.None
                onSave(
                    stage.copy(
                        name = name,
                        durationMs = duration,
                        openingSoundId = openingId,
                        closingSoundId = closingId,
                        intervalPlan = plan,
                        ambienceLayers = layers.toList(),
                    ),
                )
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
