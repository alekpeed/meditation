package com.meditation.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CounterScreen(onBack: () -> Unit) {
    var count by remember { mutableIntStateOf(0) }
    var target by remember { mutableIntStateOf(108) }
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Counter", style = MaterialTheme.typography.headlineSmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
            listOf(27, 54, 108, 0).forEach { t ->
                FilterChip(
                    selected = target == t,
                    onClick = { target = t; if (count > 0) count = 0 },
                    label = { Text(if (t == 0) "∞" else t.toString()) },
                )
            }
        }

        // Tap anywhere in this large area to advance the count.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(interactionSource = interaction, indication = null) {
                    count++
                    val done = target != 0 && count >= target
                    haptics.performHapticFeedback(if (done) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (target == 0) "$count" else "$count / $target",
                    fontSize = 64.sp,
                    style = MaterialTheme.typography.displayLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (target != 0 && count >= target) "Complete" else "Tap to count",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { count = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Reset") }
        Spacer(Modifier.height(8.dp))
    }
}
