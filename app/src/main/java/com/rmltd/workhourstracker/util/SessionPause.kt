package com.rmltd.workhourstracker.util

import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure helpers for session pause elapsed display (1.3.40).
 * Pause freezes the UI clock; it does not close an OPEN punch.
 */
object SessionPause {

    /**
     * Elapsed wall millis from today's [clockInMinutes] to [endEpochMillis].
     * Negative (clock skew) clamps to 0.
     */
    fun elapsedMillis(
        clockInMinutes: Int,
        endEpochMillis: Long,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val inMillis = dayStart + clockInMinutes.toLong() * 60_000L
        return (endEpochMillis - inMillis).coerceAtLeast(0L)
    }

    /** HH:MM:SS from elapsed millis. */
    fun formatHms(elapsedMillis: Long): String {
        val totalSec = (elapsedMillis / 1000L).coerceAtLeast(0L)
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val s = totalSec % 60L
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    /**
     * Effective "now" minutes-from-midnight for [SessionElapsed] when paused.
     * Uses freeze wall time on [today]; falls back to [liveNowMinutes] if freeze null.
     */
    fun effectiveNowMinutes(
        liveNowMinutes: Int,
        freezeEpochMillis: Long?,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int {
        if (freezeEpochMillis == null) return liveNowMinutes
        val zdt = java.time.Instant.ofEpochMilli(freezeEpochMillis).atZone(zone)
        if (zdt.toLocalDate() != today) return liveNowMinutes
        return (zdt.hour * 60 + zdt.minute).coerceIn(0, HoursCalc.MINUTES_PER_DAY - 1)
    }
}
