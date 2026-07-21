package com.meditation.app.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class BreathPhase(val label: String) {
    INHALE("Breathe in"), HOLD("Hold"), EXHALE("Breathe out"), REST("Rest")
}

@Composable
fun BreathingScreen(onBack: () -> Unit) {
    var inhale by remember { mutableIntStateOf(4) }
    var hold by remember { mutableIntStateOf(4) }
    var exhale by remember { mutableIntStateOf(4) }
    var rest by remember { mutableIntStateOf(2) }
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(BreathPhase.INHALE) }
    var target by remember { mutableFloatStateOf(0.4f) }
    var animMs by remember { mutableIntStateOf(4000) }

    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = animMs.coerceAtLeast(1), easing = LinearEasing),
        label = "breath",
    )

    LaunchedEffect(running, inhale, hold, exhale, rest) {
        if (!running) { target = 0.4f; animMs = 800; phase = BreathPhase.INHALE; return@LaunchedEffect }
        while (isActive && running) {
            for (p in BreathPhase.entries) {
                val secs = when (p) {
                    BreathPhase.INHALE -> inhale; BreathPhase.HOLD -> hold
                    BreathPhase.EXHALE -> exhale; BreathPhase.REST -> rest
                }
                if (secs <= 0) continue
                phase = p
                animMs = secs * 1000
                target = when (p) {
                    BreathPhase.INHALE, BreathPhase.HOLD -> 1f
                    BreathPhase.EXHALE, BreathPhase.REST -> 0.4f
                }
                delay(secs * 1000L)
            }
        }
    }

    val ring = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Breathing", style = MaterialTheme.typography.headlineSmall)
        }

        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.75f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    drawCircle(ring.copy(alpha = 0.18f), radius = r)
                    drawCircle(ring, radius = r * scale)
                }
                Text(if (running) phase.label else "Ready", style = MaterialTheme.typography.titleLarge)
            }
        }

        Column(Modifier.fillMaxWidth()) {
            Stepper("Inhale", inhale, enabled = !running) { inhale = it }
            Stepper("Hold", hold, enabled = !running) { hold = it }
            Stepper("Exhale", exhale, enabled = !running) { exhale = it }
            Stepper("Rest", rest, enabled = !running) { rest = it }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = { running = !running }, modifier = Modifier.fillMaxWidth()) {
            Text(if (running) "Stop" else "Start")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Stepper(label: String, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(enabled = enabled && value > 0, onClick = { onChange((value - 1).coerceAtLeast(0)) }) { Text("−") }
            Text("${value}s", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(enabled = enabled && value < 20, onClick = { onChange((value + 1).coerceAtMost(20)) }) { Text("+") }
        }
    }
}
