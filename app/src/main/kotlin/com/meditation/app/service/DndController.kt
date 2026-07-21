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
    private var previousFilter: Int? = null

    val hasAccess: Boolean get() = nm.isNotificationPolicyAccessGranted

    /** Raise the interruption filter for the session, remembering the prior value. */
    fun enable(priorityOnly: Boolean = true) {
        if (!hasAccess) return
        if (previousFilter == null) previousFilter = nm.currentInterruptionFilter
        nm.interruptionFilter =
            if (priorityOnly) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_NONE
    }

    /** Restore whatever filter was active before [enable]; no-op if we never changed it. */
    fun restore() {
        if (!hasAccess) return
        previousFilter?.let { nm.interruptionFilter = it }
        previousFilter = null
    }
}
