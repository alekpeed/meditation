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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meditation.app.ui.Format
import com.meditation.app.ui.MeditationViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatisticsScreen(vm: MeditationViewModel, onBack: () -> Unit) {
    val s by vm.statistics.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Statistics", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Total time", Format.durationWords(s.totalActiveMs))
            Tile("Sessions", s.sessionCount.toString())
            Tile("Average", Format.durationWords(s.averageMs))
            Tile("Longest", Format.durationWords(s.longestMs))
            Tile("Current streak", "${s.currentStreakDays} day${plural(s.currentStreakDays)}")
            Tile("Longest streak", "${s.longestStreakDays} day${plural(s.longestStreakDays)}")
            Tile("This week", Format.durationWords(s.weeklyTotalMs))
            Tile("This month", Format.durationWords(s.monthlyTotalMs))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Statistics reflect completed sessions only. Streaks are simply consecutive days you sat — no scoring, no comparison.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun plural(n: Int) = if (n == 1) "" else "s"

@Composable
private fun Tile(label: String, value: String) {
    Card(Modifier.fillMaxWidth(0.46f)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
