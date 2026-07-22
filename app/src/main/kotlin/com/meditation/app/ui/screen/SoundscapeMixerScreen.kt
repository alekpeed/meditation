package com.meditation.app.ui.screen

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meditation.core.AmbienceLayer
import com.meditation.core.SoundRole

/**
 * Stack up to three ambience layers, preview the mix live, and save it under a name for reuse
 * across presets (brief §15 ambient/soundscape mixer). A saved mix is just data — applying it to
 * a stage happens from the Stage editor, which offers any saved mix as a one-tap starting point.
 */
@Composable
fun SoundscapeMixerScreen(vm: com.meditation.app.ui.MeditationViewModel, onDone: () -> Unit) {
    val sounds by vm.sounds.collectAsStateWithLifecycle()
    val savedMixes by vm.savedMixes.collectAsStateWithLifecycle()
    val layers: SnapshotStateList<AmbienceLayer> = remember { mutableStateListOfDefault() }
    var mixName by remember { mutableStateOf("") }

    fun currentLayers() = layers.map { it.soundId to it.volume }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.stopPreview(); onDone() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Soundscape mixer", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))

        Column(Modifier.verticalScroll(rememberScrollState())) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Layers (up to 3)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
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
                                val firstAmbience = sounds.firstOrNull { s -> SoundRole.AMBIENCE in s.roles && layers.none { it.soundId == s.id } }
                                if (firstAmbience != null) layers.add(AmbienceLayer(firstAmbience.id, 0.6))
                            }) { Text("Add layer") }
                        }
                        if (layers.isNotEmpty()) {
                            OutlinedButton(onClick = { vm.previewMix(currentLayers()) }) { Text("Preview") }
                            OutlinedButton(onClick = { vm.stopPreview() }) { Text("Stop") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(mixName, { mixName = it }, label = { Text("Mix name") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = layers.isNotEmpty(),
                        onClick = {
                            vm.saveMix(mixName, currentLayers())
                            mixName = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save mix") }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Saved mixes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (savedMixes.isEmpty()) {
                Text(
                    "No saved mixes yet — build one above and save it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            savedMixes.forEach { mix ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(mix.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${mix.layers.size} layer${if (mix.layers.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.previewMix(mix.layers.map { it.soundId to it.volume }) }) {
                            Icon(Icons.Filled.PlayArrow, "Preview")
                        }
                        IconButton(onClick = { vm.deleteMix(mix.id) }) { Icon(Icons.Filled.Delete, "Delete") }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun mutableStateListOfDefault(): SnapshotStateList<AmbienceLayer> = mutableListOf<AmbienceLayer>().toMutableStateList()
