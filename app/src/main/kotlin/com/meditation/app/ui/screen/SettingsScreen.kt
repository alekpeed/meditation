package com.meditation.app.ui.screen

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meditation.app.BuildConfig
import com.meditation.app.MeditationApp
import com.meditation.app.alarm.AlarmScheduler
import com.meditation.app.ui.MeditationViewModel

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MeditationViewModel) {
    val context = LocalContext.current
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val presets by vm.presets.collectAsStateWithLifecycle()
    val autoRules by vm.autoPresetRules.collectAsStateWithLifecycle()
    val container = MeditationApp.from(context).container

    val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val exactAlarmOn = rememberExactAlarm(context)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        SectionCard("Audio") {
            LabeledSlider("Bell volume", prefs.bellVolume.toFloat()) { vm.setBellVolume(it.toDouble()) }
            LabeledSlider("Ambience volume", prefs.ambienceVolume.toFloat()) { vm.setAmbienceVolume(it.toDouble()) }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("Appearance") {
            Text("Theme", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = prefs.theme == key,
                        onClick = { vm.setTheme(key) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Palette", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.meditation.app.ui.theme.Palettes.all.forEach { palette ->
                    androidx.compose.material3.FilterChip(
                        selected = prefs.palette == palette.key,
                        onClick = { vm.setPalette(palette.key) },
                        label = { Text(palette.title) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Timer face", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ring" to "Ring", "digits" to "Digits", "minimal" to "Minimal").forEach { (key, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = prefs.timerFace == key,
                        onClick = { vm.setTimerFace(key) },
                        label = { Text(label) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("Session") {
            ToggleRow("Keep screen on", "Prevent the display from sleeping during a session.", prefs.keepScreenOn) {
                vm.setKeepScreenOn(it)
            }
            ToggleRow("Reduced motion", "Turn off the breathing animation on the timer.", prefs.reducedMotion) {
                vm.setReducedMotion(it)
            }
            ToggleRow("Silence notifications", "Turn on Do Not Disturb while a session runs (needs access below).", prefs.dndDuringSession) {
                vm.setDndDuringSession(it)
            }
            ToggleRow("Spoken cues", "Announce each stage's name and any cues you've written for it.", prefs.spokenCuesEnabled) {
                vm.setSpokenCuesEnabled(it)
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("Background operation") {
            StatusRow("Notifications", if (notificationsOn) "Enabled" else "Disabled") {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            StatusRow("Exact alarms", if (exactAlarmOn) "Allowed" else "Not allowed") {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
            val dndAccess = (context.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager).isNotificationPolicyAccessGranted
            StatusRow("Do Not Disturb access", if (dndAccess) "Allowed" else "Not allowed") {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            Text(
                "Battery optimization can delay background timers on some devices. This app cannot " +
                    "guarantee an exemption; exact alarms are used as a fallback for the final bell.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("Reminders") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Daily reminder", style = MaterialTheme.typography.bodyLarge)
                androidx.compose.material3.Switch(
                    checked = prefs.reminderEnabled,
                    onCheckedChange = { vm.setReminder(it, prefs.reminderHour, prefs.reminderMinute) },
                )
            }
            if (prefs.reminderEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Time", style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.setReminder(true, (prefs.reminderHour + 23) % 24, prefs.reminderMinute) }) { Text("−h") }
                        Text(String.format(java.util.Locale.US, "%02d:%02d", prefs.reminderHour, prefs.reminderMinute), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { vm.setReminder(true, (prefs.reminderHour + 1) % 24, prefs.reminderMinute) }) { Text("+h") }
                        TextButton(onClick = { vm.setReminder(true, prefs.reminderHour, (prefs.reminderMinute + 45) % 60) }) { Text("−m") }
                        TextButton(onClick = { vm.setReminder(true, prefs.reminderHour, (prefs.reminderMinute + 15) % 60) }) { Text("+m") }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("Auto-presets") {
            Text(
                "Suggest a session on the Home screen depending on the time of day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (presets.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Create a preset first to assign it to a time of day.", style = MaterialTheme.typography.bodyMedium)
            } else {
                com.meditation.app.ui.AutoPresetWindows.all.forEach { window ->
                    val currentId = autoRules.firstOrNull {
                        it.startMinute == window.startMinute && it.endMinute == window.endMinute
                    }?.presetId
                    Spacer(Modifier.height(10.dp))
                    Text(window.label, style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        androidx.compose.material3.FilterChip(
                            selected = currentId == null,
                            onClick = { vm.setAutoPresetForWindow(window.key, null) },
                            label = { Text("Off") },
                        )
                        presets.forEach { p ->
                            androidx.compose.material3.FilterChip(
                                selected = currentId == p.id,
                                onClick = { vm.setAutoPresetForWindow(window.key, p.id) },
                                label = { Text(p.name) },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportJson().toByteArray()) }
        }
        val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportCsv().toByteArray()) }
        }
        val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportBackupJson().toByteArray()) }
        }
        val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                if (text != null) vm.importBackupJson(text)
            }
        }
        SectionCard("Data") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportBackup.launch("meditation-backup.json") }) { Text("Back up") }
                Button(onClick = { importBackup.launch(arrayOf("application/json")) }) { Text("Restore") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { exportJson.launch("meditation-history.json") }) { Text("Export JSON") }
                TextButton(onClick = { exportCsv.launch("meditation-history.csv") }) { Text("Export CSV") }
            }
            Text(
                "Back up creates a full snapshot (presets, history, favorites, settings) you can restore " +
                    "later or on another device. Everything stays local unless you share the file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard("About") {
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "All sessions, sounds, and history stay on this device. No account, network, or analytics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Technical limitation: after an explicit force-stop from Android settings, timers, " +
                    "alarms, and audio cannot run until you reopen the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun rememberExactAlarm(context: android.content.Context): Boolean =
    AlarmScheduler(context).canScheduleExact()

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text("$label · ${(value * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}

@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusRow(label: String, status: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text("Open") }
    }
}
