package com.meditation.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.meditation.core.SessionTemplates

@Composable
fun SessionsScreen(
    vm: MeditationViewModel,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenRetreatBuilder: () -> Unit = {},
) {
    val presets by vm.presets.collectAsStateWithLifecycle()
    var showTemplates by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Sessions", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showTemplates = true }) { Text("Templates") }
                Button(onClick = onCreate) { Text("New") }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (presets.isEmpty()) {
            Text(
                "No presets yet. Start from a ready-made Template or build your own with New.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(presets, key = { it.id }) { preset ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(preset.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${Format.durationWords(preset.plannedDurationMs)} · ${preset.stages.size} stage${if (preset.stages.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { vm.savePreset(preset.copy(favorite = !preset.favorite)) }) {
                                Icon(
                                    if (preset.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Favorite",
                                )
                            }
                            IconButton(onClick = { onEdit(preset.id) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { vm.deletePreset(preset.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                            Button(onClick = { vm.start(preset) }) { Text("Start") }
                        }
                    }
                }
            }
        }
    }

    if (showTemplates) {
        TemplatePickerDialog(
            onDismiss = { showTemplates = false },
            onPick = { key ->
                showTemplates = false
                if (key == "retreat") onOpenRetreatBuilder() else vm.createFromTemplate(key)
            },
        )
    }
}

@Composable
private fun TemplatePickerDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Start from a template") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SessionTemplates.catalog.forEach { template ->
                    Card(
                        Modifier.fillMaxWidth().clickable { onPick(template.key) },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(template.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}
