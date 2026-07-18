package com.meditation.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.meditation.app.R
import com.meditation.app.ui.MainActivity
import com.meditation.core.SessionSnapshot
import com.meditation.core.SessionStatus

/**
 * Builds the ongoing session notification and the separate completion notification.
 *
 * The ongoing notification uses the system chronometer (count-up or count-down) so the remaining
 * time animates without one notification post per second (brief §14, backlog C3). Actions invoke
 * [NotificationActionReceiver] directly, so Pause/Resume, Add 5, and Finish work without opening the
 * app (acceptance #5, #6).
 */
class NotificationController(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val session = NotificationChannel(
            CHANNEL_SESSION,
            context.getString(R.string.channel_session_name),
            NotificationManager.IMPORTANCE_LOW, // silent, ongoing — the bells are the audio, not this
        ).apply {
            description = context.getString(R.string.channel_session_desc)
            setShowBadge(false)
        }
        val completion = NotificationChannel(
            CHANNEL_COMPLETION,
            context.getString(R.string.channel_completion_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_completion_desc) }
        manager.createNotificationChannel(session)
        manager.createNotificationChannel(completion)
    }

    fun buildOngoing(snapshot: SessionSnapshot?): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_SESSION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(snapshot?.presetName ?: context.getString(R.string.notif_session_title))
            .setContentText(subtitle(snapshot))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        applyChronometer(builder, snapshot)

        val paused = snapshot?.status == SessionStatus.PAUSED
        if (paused) {
            builder.addAction(0, context.getString(R.string.action_resume), action(NotificationActionReceiver.ACTION_RESUME))
        } else {
            builder.addAction(0, context.getString(R.string.action_pause), action(NotificationActionReceiver.ACTION_PAUSE))
        }
        builder.addAction(0, context.getString(R.string.action_add_five), action(NotificationActionReceiver.ACTION_ADD_FIVE))
        builder.addAction(0, context.getString(R.string.action_finish), action(NotificationActionReceiver.ACTION_FINISH))
        return builder.build()
    }

    fun showCompletion(presetName: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_COMPLETION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_complete_title))
            .setContentText(presetName)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        manager.notify(COMPLETION_ID, n)
    }

    fun update(snapshot: SessionSnapshot?) {
        manager.notify(ONGOING_ID, buildOngoing(snapshot))
    }

    private fun applyChronometer(builder: NotificationCompat.Builder, snapshot: SessionSnapshot?) {
        snapshot ?: return
        if (!snapshot.running) {
            builder.setUsesChronometer(false)
            return
        }
        val now = System.currentTimeMillis()
        if (snapshot.overtime || snapshot.remainingMs <= 0) {
            // Count up (open-ended / overtime): base in the past.
            builder.setUsesChronometer(true)
            builder.setWhen(now - snapshot.countUpMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(false)
        } else {
            // Count down to the end instant.
            builder.setUsesChronometer(true)
            builder.setWhen(now + snapshot.remainingMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(true)
        }
    }

    private fun subtitle(snapshot: SessionSnapshot?): String = when {
        snapshot == null -> ""
        snapshot.status == SessionStatus.PREPARING -> "Preparing…"
        snapshot.status == SessionStatus.PAUSED -> "Paused"
        snapshot.status == SessionStatus.OVERTIME -> "Overtime"
        snapshot.stageCount > 1 && snapshot.stageName != null ->
            "Stage ${snapshot.stageIndex + 1}/${snapshot.stageCount} · ${snapshot.stageName}"
        else -> snapshot.stageName ?: ""
    }

    private fun action(actionName: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(actionName)
        return PendingIntent.getBroadcast(
            context, actionName.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_SESSION = "session"
        const val CHANNEL_COMPLETION = "completion"
        const val ONGOING_ID = 1001
        const val COMPLETION_ID = 1002
    }
}
