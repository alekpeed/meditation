package com.meditation.app.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.meditation.app.MeditationApp
import com.meditation.core.SessionStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

        // Render controller state; stop cleanly once nothing needs the process alive. A continuous
        // preview (ambience or generated noise) counts as well as a session — otherwise previewed
        // audio dies as soon as the user leaves the app.
        lifecycleScope.launch {
            combine(
                container.controller.snapshot,
                container.audio.previewActive,
            ) { snap, previewing -> snap to previewing }.collectLatest { (snap, previewing) ->
                // The controller posts the completion notification and clears state on terminal;
                // the service just renders active state and stops when nothing is playing.
                val sessionOver = snap == null ||
                    snap.status == SessionStatus.COMPLETED ||
                    snap.status == SessionStatus.CANCELLED
                when {
                    !sessionOver -> notifications.update(snap)
                    previewing -> notifications.update(null)
                    else -> stopSelfSafely()
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
