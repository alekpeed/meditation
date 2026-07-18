package com.meditation.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Schedules the single final-session event with [AlarmManager] as a fallback that fires even if the
 * foreground service is evicted from memory (brief §4.4). Uses exact scheduling when permitted and
 * degrades to an inexact-but-allowed alarm otherwise, so a missing permission never crashes.
 *
 * Interval bells are intentionally NOT scheduled here — Android throttles frequent idle alarms, so
 * those are driven by the active foreground service instead.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    fun scheduleFinal(sessionId: String, triggerWallMs: Long) {
        val pi = pendingIntent(sessionId)
        try {
            if (canScheduleExact()) {
                // Wake the device precisely at the end so the final bell is on time with screen off.
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerWallMs, pi)
            } else {
                // Degraded path: still allowed while idle, just not guaranteed exact.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerWallMs, pi)
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "Exact alarm denied; falling back to inexact", se)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerWallMs, pi)
        }
    }

    fun cancel(sessionId: String) {
        alarmManager.cancel(pendingIntent(sessionId))
    }

    private fun pendingIntent(sessionId: String): PendingIntent {
        val intent = Intent(context, FinalAlarmReceiver::class.java).apply {
            action = FinalAlarmReceiver.ACTION_FINAL
            putExtra(FinalAlarmReceiver.EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE, // stable code: rescheduling replaces the previous alarm rather than stacking
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQUEST_CODE = 4201
    }
}
