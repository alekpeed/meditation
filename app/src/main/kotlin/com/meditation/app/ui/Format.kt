package com.meditation.app.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Formatting helpers for durations and timestamps used across screens. */
object Format {
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0)) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun durationWords(ms: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
        return when {
            minutes < 1 -> "<1 min"
            minutes < 60 -> "$minutes min"
            minutes % 60 == 0L -> "${minutes / 60} h"
            else -> "${minutes / 60} h ${minutes % 60} min"
        }
    }

    private val dateFmt = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.getDefault())
    fun dateTime(wallMs: Long): String = dateFmt.format(Date(wallMs))
}
