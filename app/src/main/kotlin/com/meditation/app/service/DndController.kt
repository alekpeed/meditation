package com.meditation.app.service

import android.app.NotificationManager
import android.content.Context

/**
 * Do-Not-Disturb integration (brief §15). While a session runs the app can silence interruptions by
 * raising the system interruption filter, then restore whatever the user had before. This requires
 * the one-time "Do Not Disturb access" grant; without it every call is a safe no-op so a session
 * never crashes for lack of the permission.
 */
class DndController(context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    // This survives process death. DND is global device state, so keeping the old filter only in
    // memory could leave the user's phone silenced if Android kills us mid-session.
    private val state = context.getSharedPreferences("dnd_restore_state", Context.MODE_PRIVATE)

    val hasAccess: Boolean get() = nm.isNotificationPolicyAccessGranted

    /** Raise the interruption filter for the session, remembering the prior value. */
    @Synchronized
    fun enable(priorityOnly: Boolean = true) {
        if (!hasAccess) return
        if (!state.getBoolean(KEY_CHANGED, false)) {
            // Commit before changing the system setting: a process kill immediately after the
            // call must still leave enough information for a later restore.
            val saved = state.edit()
                .putBoolean(KEY_CHANGED, true)
                .putInt(KEY_PREVIOUS_FILTER, nm.currentInterruptionFilter)
                .commit()
            if (!saved) return
        }
        val changed = runCatching {
            nm.setInterruptionFilter(
            if (priorityOnly) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_NONE,
            )
        }.isSuccess
        // Do not claim ownership when Android rejected the change.
        if (!changed) state.edit().clear().commit()
    }

    /** Restore whatever filter was active before [enable]; no-op if we never changed it. */
    @Synchronized
    fun restore() {
        if (!hasAccess) return
        if (!state.getBoolean(KEY_CHANGED, false)) return
        val previous = state.getInt(KEY_PREVIOUS_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        // Keep the durable marker when the platform rejects the restore. A future startup can
        // retry rather than silently abandoning the user's pre-session DND state.
        if (runCatching { nm.setInterruptionFilter(previous) }.isSuccess) {
            state.edit().clear().commit()
        }
    }

    private companion object {
        const val KEY_CHANGED = "changed_by_meditation_session"
        const val KEY_PREVIOUS_FILTER = "previous_interruption_filter"
    }
}
