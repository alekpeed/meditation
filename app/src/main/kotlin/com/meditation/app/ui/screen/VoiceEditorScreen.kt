package com.meditation.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meditation.core.SoundCategory

/**
 * Shape a custom synthesized bell/bowl/gong/wood/chime voice (brief §16 synthesized bowl/chime
 * editor) by choosing a category and descriptive tags — the same vocabulary
 * [com.meditation.app.audio.ToneSynth] already reads to voice bundled sounds that have no
 * recording. Saved sounds play through that existing synthesis path.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceEditorScreen(vm: com.meditation.app.ui.MeditationViewModel, onDone: () -> Unit) {
    var name by remember { mutableStateOf("My Bowl") }
    var category by remember { mutableStateOf(SoundCategory.BOWL) }
    val tags = remember { mutableStateListOf("warm") }

    val voiceableCategories = listOf(SoundCategory.BELL, SoundCategory.BOWL, SoundCategory.GONG, SoundCategory.WOOD, SoundCategory.CHIME)
    val availableTags = listOf("bright", "clear", "light", "airy", "high", "deep", "low", "warm", "soft")

    fun toggle(tag: String) {
        if (tag in tags) tags.remove(tag) else tags.add(tag)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.stopPreview(); onDone() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Bowl / chime editor", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("Category", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    voiceableCategories.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Character", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    availableTags.forEach { tag ->
                        FilterChip(selected = tag in tags, onClick = { toggle(tag) }, label = { Text(tag) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.previewSynthAsset(category, tags.toList()) }) { Text("Preview") }
                    OutlinedButton(onClick = { vm.stopPreview() }) { Text("Stop") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                vm.saveCustomStrikeVoice(name, category, tags.toList())
                vm.stopPreview()
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save voice") }
        Spacer(Modifier.height(24.dp))
    }
}
