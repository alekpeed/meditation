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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.meditation.core.GeneratorConfig
import com.meditation.core.SoundCategory

/**
 * Build a custom sine or two-oscillator drone (brief §16 multi-oscillator drone editor), preview
 * it live, and save it as a new generated ambience sound alongside the bundled drones.
 */
@Composable
fun DroneEditorScreen(vm: com.meditation.app.ui.MeditationViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("My Drone") }
    var dual by remember { mutableStateOf(false) }
    var freq1Text by remember { mutableStateOf("110") }
    var freq2Text by remember { mutableStateOf("165") }
    var mix by remember { mutableFloatStateOf(0.5f) }
    var gain by remember { mutableFloatStateOf(0.45f) }

    fun buildConfig() = GeneratorConfig(
        type = if (dual) "dual" else "sine",
        gain = gain.toDouble(),
        frequencyHz = freq1Text.toDoubleOrNull()?.coerceIn(30.0, 800.0) ?: 110.0,
        secondFrequencyHz = if (dual) freq2Text.toDoubleOrNull()?.coerceIn(30.0, 800.0) ?: 165.0 else null,
        mix = if (dual) mix.toDouble() else null,
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.stopPreview(); onDone() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Drone editor", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !dual, onClick = { dual = false }, label = { Text("Single oscillator") })
                    FilterChip(selected = dual, onClick = { dual = true }, label = { Text("Two oscillators") })
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    freq1Text, { freq1Text = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text(if (dual) "Frequency 1 (Hz)" else "Frequency (Hz)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dual) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        freq2Text, { freq2Text = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Frequency 2 (Hz)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Mix · ${(mix * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = mix, onValueChange = { mix = it }, valueRange = 0f..1f)
                }
                Spacer(Modifier.height(8.dp))
                Text("Volume · ${(gain * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(value = gain, onValueChange = { gain = it }, valueRange = 0.05f..1f)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.previewGeneratorConfig(buildConfig(), gain.toDouble()) }) { Text("Preview") }
                    OutlinedButton(onClick = { vm.stopPreview() }) { Text("Stop") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                vm.saveCustomDrone(name, buildConfig(), SoundCategory.DRONE)
                vm.stopPreview()
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save drone") }
        Spacer(Modifier.height(24.dp))
    }
}
