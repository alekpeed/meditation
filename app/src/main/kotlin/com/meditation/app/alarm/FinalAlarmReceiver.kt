package com.meditation.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.meditation.app.MeditationApp

/**
 * Fallback trigger for the final session event. Validates the session id and defers to the
 * controller, whose engine dedup guarantees the final bell plays exactly once even if the service
 * already fired it (acceptance #8).
 */
class FinalAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FINAL) return
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return
        val pending = goAsync()
        val controller = MeditationApp.from(context).container.controller
        val job = controller.onFinalAlarm(sessionId)
        job.invokeOnCompletion { pending.finish() }
    }

    companion object {
        const val ACTION_FINAL = "com.meditation.app.FINAL_ALARM"
        const val EXTRA_SESSION_ID = "session_id"
    }
}
