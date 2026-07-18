package com.meditation.core

/**
 * Time source abstraction.
 *
 * Two distinct clocks, mirroring the brief's non-negotiable rule #2:
 *  - [elapsedRealtimeMs] is monotonic and MUST be used for all active-timing math. It keeps
 *    counting while the device sleeps and is immune to wall-clock changes (DST, manual set, NTP).
 *    On Android this maps to android.os.SystemClock.elapsedRealtime().
 *  - [wallClockMs] is the real-world timestamp used only for history records and reboot recovery.
 *    On Android this maps to System.currentTimeMillis().
 *
 * The engine never derives remaining time from [wallClockMs]; injecting a fake clock in tests
 * lets us prove that a wall-clock jump cannot corrupt an in-flight session.
 */
interface Clock {
    fun elapsedRealtimeMs(): Long
    fun wallClockMs(): Long
}

/** A deterministic clock for tests. Advance [elapsed] and [wall] independently. */
class FakeClock(
    var elapsed: Long = 0,
    var wall: Long = 1_700_000_000_000,
) : Clock {
    override fun elapsedRealtimeMs(): Long = elapsed
    override fun wallClockMs(): Long = wall

    /** Advance both clocks by the same amount, as normal time passing. */
    fun advance(ms: Long) {
        elapsed += ms
        wall += ms
    }
}
