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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meditation.app.ui.Format
import com.meditation.app.ui.MeditationViewModel
import com.meditation.core.SessionPreset
import com.meditation.core.SessionStage
import com.meditation.core.SoundRole
import java.util.UUID

@Composable
fun SessionBuilderScreen(vm: MeditationViewModel, presetId: String?, onDone: () -> Unit) {
    val presets by vm.presets.collectAsStateWithLifecycle()
    val sounds by vm.sounds.collectAsStateWithLifecycle()
    val existing = remember(presets, presetId) { presets.firstOrNull { it.id == presetId } }

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var prepMinutes by remember(existing) { mutableStateOf(((existing?.preparationMs ?: 0) / 60_000).toString()) }
    var finalSoundId by remember(existing) { mutableStateOf(existing?.finalSoundId) }
    var strikeCount by remember(existing) { mutableStateOf((existing?.completionStrikeCount ?: 1).toString()) }
    val stages: SnapshotStateList<SessionStage> = remember(existing) {
        (existing?.stages ?: listOf(SessionStage(UUID.randomUUID().toString(), "Meditation", 10 * 60_000L)))
            .toMutableStateList()
    }
    var editing by remember { mutableStateOf<Int?>(null) }

    fun buildPreset() = SessionPreset(
        id = existing?.id ?: "preset-${UUID.randomUUID()}",
        name = name.trim(),
        preparationMs = (prepMinutes.toLongOrNull() ?: 0) * 60_000,
        stages = stages.toList(),
        finalSoundId = finalSoundId,
        completionStrikeCount = strikeCount.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        overtimeMode = existing?.overtimeMode ?: com.meditation.core.OvertimeMode.STOP,
        favorite = existing?.favorite ?: false,
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    val errors = remember(name, prepMinutes, finalSoundId, strikeCount, stages.toList()) {
        vm.validationErrors(buildPreset())
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(if (existing == null) "New preset" else "Edit preset", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))

        SectionCardB("Basic details") {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                prepMinutes, { prepMinutes = it.filter(Char::isDigit) },
                label = { Text("Preparation (minutes)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCardB("Stages") {
            stages.forEachIndexed { index, stage ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stage.name.ifBlank { "Stage ${index + 1}" }, style = MaterialTheme.typography.titleSmall)
                            Text(
                                stage.durationMs?.let { Format.durationWords(it) } ?: "Open-ended",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { if (index > 0) stages.swap(index, index - 1) }) {
                            Icon(Icons.Filled.ArrowUpward, "Move up")
                        }
                        IconButton(onClick = { if (index < stages.lastIndex) stages.swap(index, index + 1) }) {
                            Icon(Icons.Filled.ArrowDownward, "Move down")
                        }
                        IconButton(onClick = { editing = index }) { Icon(Icons.Filled.Edit, "Edit") }
                        IconButton(onClick = { if (stages.size > 1) stages.removeAt(index) }) {
                            Icon(Icons.Filled.Delete, "Delete")
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { stages.add(SessionStage(UUID.randomUUID().toString(), "Stage ${stages.size + 1}", 5 * 60_000L)) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) { Text("Add stage") }
        }

        Spacer(Modifier.height(12.dp))
        SectionCardB("Completion") {
            SoundPicker("Final sound", SoundRole.CLOSING, sounds, finalSoundId, { finalSoundId = it })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                strikeCount, { strikeCount = it.filter(Char::isDigit) },
                label = { Text("Completion strikes") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        if (errors.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Fix before saving", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = errors.isEmpty(),
                onClick = { vm.savePreset(buildPreset()); onDone() },
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
            Button(
                enabled = errors.isEmpty(),
                onClick = { val p = buildPreset(); vm.savePreset(p); vm.start(p); onDone() },
                modifier = Modifier.weight(1f),
            ) { Text("Save & start") }
        }
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { index ->
        StageEditorDialog(
            stage = stages[index],
            isLast = index == stages.lastIndex,
            sounds = sounds,
            onPreviewMix = { layers -> vm.previewMix(layers) },
            onStopPreview = { vm.stopPreview() },
            onDismiss = { vm.stopPreview(); editing = null },
            onSave = { updated -> stages[index] = updated; vm.stopPreview(); editing = null },
        )
    }
}

private fun <T> SnapshotStateList<T>.swap(a: Int, b: Int) {
    val tmp = this[a]; this[a] = this[b]; this[b] = tmp
}

@Composable
private fun SectionCardB(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
