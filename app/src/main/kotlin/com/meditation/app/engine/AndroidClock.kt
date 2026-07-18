package com.meditation.app.engine

import android.os.SystemClock
import com.meditation.core.Clock

/**
 * Production clock. [elapsedRealtimeMs] uses [SystemClock.elapsedRealtime] — monotonic, counts
 * during deep sleep, and is immune to wall-clock changes — for all active-timing math.
 * [wallClockMs] uses [System.currentTimeMillis] only for history and reboot reconstruction.
 */
object AndroidClock : Clock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
    override fun wallClockMs(): Long = System.currentTimeMillis()
}
