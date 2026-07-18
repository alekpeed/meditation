package com.meditation.app.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.meditation.app.MeditationApp
import com.meditation.core.SessionStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground host that keeps the session process alive with the screen off (brief §4.3).
 *
 * The service does not own timing — it renders the controller's [com.meditation.core.SessionSnapshot]
 * into the ongoing notification and holds the mediaPlayback foreground type so ambient audio and
 * interval events continue in the background. It starts from the user-initiated Start action while
 * the app is foregrounded, and stops itself cleanly once the session is no longer active.
 */
class MeditationService : LifecycleService() {

    private lateinit var notifications: NotificationController

    override fun onCreate() {
        super.onCreate()
        val container = MeditationApp.from(this).container
        notifications = container.notifications
        notifications.ensureChannels()

        // Post the ongoing notification immediately (required within the FGS start window).
        startAsForeground()

        // Render controller state; stop cleanly when the session ends or is cleared.
        lifecycleScope.launch {
            container.controller.snapshot.collectLatest { snap ->
                if (snap == null) {
                    stopSelfSafely()
                } else if (!snap.running && snap.status == SessionStatus.COMPLETED) {
                    notifications.showCompletion(snap.presetName)
                    stopSelfSafely()
                } else if (snap.status == SessionStatus.CANCELLED) {
                    stopSelfSafely()
                } else {
                    notifications.update(snap)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // If the process was recreated, rebuild session state from persistence.
        MeditationApp.from(this).container.controller.restore()
        return START_STICKY
    }

    private fun startAsForeground() {
        val snap = MeditationApp.from(this).container.controller.snapshot.value
        val notification = notifications.buildOngoing(snap)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationController.ONGOING_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NotificationController.ONGOING_ID, notification)
        }
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
    }
}
