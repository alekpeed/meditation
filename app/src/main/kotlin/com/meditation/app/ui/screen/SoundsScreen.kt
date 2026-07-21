package com.meditation.app.ui.screen

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meditation.app.ui.Format
import com.meditation.app.ui.MeditationViewModel
import com.meditation.core.SoundAsset
import com.meditation.core.SoundCategory

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SoundsScreen(vm: MeditationViewModel) {
    val context = LocalContext.current
    val sounds by vm.sounds.collectAsStateWithLifecycle()
    val attributions by vm.attributions.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf<SoundCategory?>(null) }
    var detail by remember { mutableStateOf<SoundAsset?>(null) }

    // Storage Access Framework import: copy the chosen file into app-private storage.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else "Imported sound"
            } ?: "Imported sound"
            vm.importAudio(context, uri, name.substringBeforeLast('.'))
        }
    }

    val visible = remember(sounds, category) {
        sounds.filter { category == null || it.category == category }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Sounds", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { importLauncher.launch(arrayOf("audio/*")) }) { Text("Import") }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
            SoundCategory.entries.forEach { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { category = c },
                    label = { Text(c.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { sound ->
                Card(Modifier.fillMaxWidth().clickable { detail = sound }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sound.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                sound.tags.take(3).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { vm.toggleSoundFavorite(sound.id, !sound.favorite) }) {
                            Icon(
                                if (sound.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = "Favorite",
                            )
                        }
                    }
                }
            }
        }
    }

    detail?.let { sound ->
        val attribution = remember(sound, attributions) { attributions.firstOrNull { it.id == sound.attributionId } }
        ModalBottomSheet(onDismissRequest = { vm.stopPreview(); detail = null }) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(sound.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    sound.category.name.lowercase().replaceFirstChar { it.uppercase() } +
                        (sound.durationMs?.let { " · ${Format.clock(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                sound.description?.let {
                    Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sound.tags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Assignable as: " + sound.roles.joinToString { it.name.lowercase() }.ifBlank { "any role" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.previewSound(sound.id) }) { Text("Play") }
                    OutlinedButton(onClick = { vm.stopPreview() }) { Text("Stop") }
                }
                attribution?.let { attr ->
                    Spacer(Modifier.height(16.dp))
                    Text("Attribution", style = MaterialTheme.typography.titleSmall)
                    val line = listOfNotNull(
                        attr.title.ifBlank { null },
                        attr.creator.ifBlank { null },
                        attr.licenseName.ifBlank { null },
                        attr.sourceName.ifBlank { null },
                    ).joinToString(" · ").ifBlank { "Licensing pending" }
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    attr.modifications?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
