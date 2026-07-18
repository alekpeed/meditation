package com.meditation.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meditation.app.MeditationApp

/**
 * Phase 2 reboot recovery (backlog L4). Alarms and services do not survive a reboot, so on
 * BOOT_COMPLETED we reload any unfinished session and let the controller reconstruct its state and
 * reschedule a still-relevant final alarm. We do NOT auto-launch media playback from boot; if
 * ambient audio needs resuming, the controller surfaces a notification asking the user to reopen.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val controller = MeditationApp.from(context).container.controller
        controller.restore().invokeOnCompletion { pending.finish() }
    }
}
