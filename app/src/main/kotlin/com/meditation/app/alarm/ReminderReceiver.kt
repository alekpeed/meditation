package com.meditation.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meditation.app.MeditationApp

/** Posts the daily reminder notification. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        MeditationApp.from(context).container.notifications.showReminder()
    }

    companion object { const val ACTION_REMIND = "com.meditation.app.REMIND" }
}
