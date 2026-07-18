package com.meditation.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meditation.app.ui.Format
import com.meditation.app.ui.MeditationViewModel
import com.meditation.core.SessionSnapshot
import com.meditation.core.SessionStatus

@Composable
fun ActiveTimerScreen(snapshot: SessionSnapshot, vm: MeditationViewModel) {
    var showAddTime by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                Text(snapshot.presetName, style = MaterialTheme.typography.titleMedium)
                if (snapshot.stageCount > 1 && snapshot.stageName != null) {
                    Text(
                        "Stage ${snapshot.stageIndex + 1} of ${snapshot.stageCount} · ${snapshot.stageName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ProgressRing(snapshot)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                snapshot.nextIntervalInMs?.let {
                    Text(
                        "Next bell in ${Format.clock(it)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                ControlBar(
                    snapshot = snapshot,
                    onPauseResume = { if (snapshot.running) vm.pause() else vm.resume() },
                    onAddTime = { showAddTime = true },
                    onFinish = { showFinish = true },
                    onSkip = vm::skip.takeIf { snapshot.stageCount > 1 && snapshot.stageIndex < snapshot.stageCount - 1 },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAddTime) AddTimeDialog(onDismiss = { showAddTime = false }) { minutes ->
        vm.extend(minutes); showAddTime = false
    }
    if (showFinish) FinishDialog(
        onDismiss = { showFinish = false },
        onSave = { vm.finish(cancelled = false); showFinish = false },
        onDiscard = { vm.finish(cancelled = true); showFinish = false },
    )
}

@Composable
private fun ProgressRing(snapshot: SessionSnapshot) {
    val countingUp = snapshot.status == SessionStatus.OVERTIME || snapshot.remainingMs <= 0
    val fraction = run {
        val remaining = snapshot.stageRemainingMs
        val elapsed = snapshot.stageElapsedMs
        if (remaining != null && (remaining + elapsed) > 0) elapsed.toFloat() / (remaining + elapsed) else 0f
    }.coerceIn(0f, 1f)

    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().rotate(-90f)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            val inset = 14.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset, size.height - inset)
            val topLeft = Offset(inset / 2, inset / 2)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = stroke)
            drawArc(ringColor, 0f, 360f * (if (countingUp) 1f else fraction), false, topLeft, arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val big = when {
                snapshot.status == SessionStatus.PREPARING -> snapshot.stageRemainingMs ?: 0
                countingUp -> snapshot.countUpMs
                else -> snapshot.remainingMs
            }
            Text(
                text = Format.clock(big),
                fontSize = 56.sp,
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                text = statusLabel(snapshot),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun statusLabel(s: SessionSnapshot): String = when (s.status) {
    SessionStatus.PREPARING -> "Preparing"
    SessionStatus.PAUSED -> "Paused"
    SessionStatus.OVERTIME -> "Overtime"
    SessionStatus.RUNNING -> if (s.remainingMs <= 0) "Open" else "Remaining"
    else -> ""
}

@Composable
private fun ControlBar(
    snapshot: SessionSnapshot,
    onPauseResume: () -> Unit,
    onAddTime: () -> Unit,
    onFinish: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onAddTime) {
            Icon(Icons.Filled.Add, contentDescription = "Add time", modifier = Modifier.size(28.dp))
        }
        FilledIconButton(onClick = onPauseResume, modifier = Modifier.size(72.dp), shape = CircleShape) {
            Icon(
                if (snapshot.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (snapshot.running) "Pause" else "Resume",
                modifier = Modifier.size(36.dp),
            )
        }
        if (onSkip != null) {
            IconButton(onClick = onSkip) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Skip stage", modifier = Modifier.size(28.dp))
            }
        } else {
            IconButton(onClick = onFinish) {
                Icon(Icons.Filled.Stop, contentDescription = "Finish", modifier = Modifier.size(28.dp))
            }
        }
    }
    if (onSkip != null) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onFinish) { Text("Finish") }
    }
}

@Composable
private fun AddTimeDialog(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add time") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10).forEach { m ->
                    Button(onClick = { onAdd(m) }) { Text("+$m min") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FinishDialog(onDismiss: () -> Unit, onSave: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish session?") },
        text = { Text("Save this session to your history, or discard it.") },
        confirmButton = { Button(onClick = onSave) { Text("Finish & save") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDiscard) { Text("Discard") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
