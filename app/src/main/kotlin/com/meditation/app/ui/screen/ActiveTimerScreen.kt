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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meditation.app.ui.Format
import com.meditation.app.ui.MeditationViewModel
import com.meditation.core.SessionSnapshot
import com.meditation.core.SessionStatus

@Composable
fun ActiveTimerScreen(snapshot: SessionSnapshot, vm: MeditationViewModel) {
    var showAddTime by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }
    val prefs by vm.preferences.collectAsStateWithLifecycle()

    // Keep the display awake for the duration of the session when the user opted in.
    val view = LocalView.current
    DisposableEffect(prefs.keepScreenOn) {
        if (prefs.keepScreenOn) view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // A slow "breath" pulse behind the ring. rememberInfiniteTransition must run unconditionally;
    // we simply ignore its value when reduced-motion is on or the session isn't running.
    val transition = rememberInfiniteTransition(label = "breath")
    val rawPulse by transition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 4200), RepeatMode.Reverse),
        label = "breathScale",
    )
    val breathScale = if (!prefs.reducedMotion && snapshot.running) rawPulse else 1f

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

            when (prefs.timerFace) {
                "digits" -> DigitsFace(snapshot)
                "minimal" -> MinimalFace(snapshot)
                else -> ProgressRing(snapshot, breathScale)
            }

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
                    onSkip = if (snapshot.stageCount > 1 && snapshot.stageIndex < snapshot.stageCount - 1) {
                        { vm.skip() }
                    } else null,
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
        onSave = { note, tags, mood -> vm.finish(cancelled = false, note = note, tags = tags, moodAfter = mood); showFinish = false },
        onDiscard = { vm.finish(cancelled = true); showFinish = false },
    )
}

/** True once the whole session (or its final stage) has moved from counting down to counting up. */
private fun isCountingUp(snapshot: SessionSnapshot): Boolean =
    snapshot.status == SessionStatus.OVERTIME || snapshot.remainingMs <= 0

/** Fraction of the current stage elapsed, 0..1 (0 when there's nothing to measure against). */
private fun stageFraction(snapshot: SessionSnapshot): Float = run {
    val remaining = snapshot.stageRemainingMs
    val elapsed = snapshot.stageElapsedMs
    if (remaining != null && (remaining + elapsed) > 0) elapsed.toFloat() / (remaining + elapsed) else 0f
}.coerceIn(0f, 1f)

/** The big clock value every face shows: prep countdown, count-up, or the normal countdown. */
private fun bigTimeMs(snapshot: SessionSnapshot): Long = when {
    snapshot.status == SessionStatus.PREPARING -> snapshot.stageRemainingMs ?: 0
    isCountingUp(snapshot) -> snapshot.countUpMs
    else -> snapshot.remainingMs
}

@Composable
private fun TimeAndStatus(snapshot: SessionSnapshot, timeStyle: androidx.compose.ui.text.TextStyle) {
    Text(text = Format.clock(bigTimeMs(snapshot)), style = timeStyle)
    Text(
        text = statusLabel(snapshot),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** A slim linear bar instead of the circular ring — easier to read at a glance, less ornamental. */
@Composable
private fun DigitsFace(snapshot: SessionSnapshot) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.8f)) {
        TimeAndStatus(snapshot, MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp))
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = { if (isCountingUp(snapshot)) 1f else stageFraction(snapshot) },
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
    }
}

/** No ring, no bar — just the numbers, for the least visual chrome during a sit. */
@Composable
private fun MinimalFace(snapshot: SessionSnapshot) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TimeAndStatus(snapshot, MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp))
    }
}

@Composable
private fun ProgressRing(snapshot: SessionSnapshot, breathScale: Float = 1f) {
    val countingUp = isCountingUp(snapshot)
    val fraction = stageFraction(snapshot)

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
            // Breathing glow: a faint filled disc that slowly expands/contracts inside the ring.
            val baseRadius = (minOf(size.width, size.height) / 2f) - inset
            drawCircle(
                color = ringColor.copy(alpha = 0.07f),
                radius = baseRadius * 0.72f * breathScale,
                center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f),
            )
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = stroke)
            drawArc(ringColor, 0f, 360f * (if (countingUp) 1f else fraction), false, topLeft, arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TimeAndStatus(snapshot, MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp))
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
private fun FinishDialog(
    onDismiss: () -> Unit,
    onSave: (note: String?, tags: List<String>, mood: Int?) -> Unit,
    onDiscard: () -> Unit,
) {
    var mood by remember { mutableStateOf(0) } // 0 = unset
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish session?") },
        text = {
            Column {
                Text("Mood after", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { m ->
                        FilterChip(selected = mood == m, onClick = { mood = if (mood == m) 0 else m }, label = { Text("$m") })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(tags, { tags = it }, label = { Text("Tags (comma-separated)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    note.ifBlank { null },
                    tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    mood.takeIf { it > 0 },
                )
            }) { Text("Finish & save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDiscard) { Text("Discard") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
