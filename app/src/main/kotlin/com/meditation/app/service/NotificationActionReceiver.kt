package com.meditation.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meditation.app.MeditationApp

/** Relays notification-button taps to the controller without opening the activity. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val controller = MeditationApp.from(context).container.controller
        val pending = goAsync()
        val job = when (intent.action) {
            ACTION_PAUSE -> controller.pause()
            ACTION_RESUME -> controller.resume()
            ACTION_ADD_FIVE -> controller.extendByMinutes(5)
            ACTION_FINISH -> controller.finish(cancelled = false)
            else -> null
        }
        if (job == null) pending.finish() else job.invokeOnCompletion { pending.finish() }
    }

    companion object {
        const val ACTION_PAUSE = "com.meditation.app.action.PAUSE"
        const val ACTION_RESUME = "com.meditation.app.action.RESUME"
        const val ACTION_ADD_FIVE = "com.meditation.app.action.ADD_FIVE"
        const val ACTION_FINISH = "com.meditation.app.action.FINISH"
    }
}
