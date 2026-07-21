package com.meditation.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.meditation.core.SoundAsset
import com.meditation.core.SoundRole

/**
 * Reusable dropdown that lists only sounds valid for [role] (screen-flow §10 "only valid roles should
 * be enabled"). A null selection means "none".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundPicker(
    label: String,
    role: SoundRole,
    sounds: List<SoundAsset>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val eligible = remember(sounds, role) {
        sounds.filter { it.roles.isEmpty() || role in it.roles }
    }
    val selectedName = eligible.firstOrNull { it.id == selectedId }?.name ?: "None"

    Box(modifier) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selectedName,
                onValueChange = {},
                readOnly = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { onSelect(null); expanded = false })
                eligible.forEach { sound ->
                    DropdownMenuItem(
                        text = { Text(sound.name) },
                        onClick = { onSelect(sound.id); expanded = false },
                    )
                }
            }
        }
    }
}
