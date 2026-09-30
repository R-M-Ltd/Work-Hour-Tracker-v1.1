package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SessionPauseTest {

    private val zone = ZoneId.of("America/Chicago")
    private val today = LocalDate.of(2026, 9, 30)

    @Test
    fun formatHms_padsComponents() {
        assertEquals("00:00:00", SessionPause.formatHms(0))
        assertEquals("01:24:08", SessionPause.formatHms(((1 * 3600) + (24 * 60) + 8) * 1000L))
        assertEquals("00:01:05", SessionPause.formatHms(65_000L))
    }

    @Test
    fun elapsedMillis_fromClockInToEnd() {
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val nineAm = 9 * 60
        val end = dayStart + ((9 * 60 + 90) * 60_000L) // 10:30
        val elapsed = SessionPause.elapsedMillis(nineAm, end, today, zone)
        assertEquals(90 * 60_000L, elapsed)
        assertEquals("01:30:00", SessionPause.formatHms(elapsed))
    }

    @Test
    fun effectiveNowMinutes_usesFreezeWhenPaused() {
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val freeze = dayStart + (10 * 60 + 15) * 60_000L // 10:15
        val live = 12 * 60 // noon
        assertEquals(
            10 * 60 + 15,
            SessionPause.effectiveNowMinutes(live, freeze, today, zone)
        )
        assertEquals(live, SessionPause.effectiveNowMinutes(live, null, today, zone))
    }

    @Test
    fun pauseDoesNotEqualClockOutSemantics() {
        // Document invariant: pause freezes display only — OPEN punch stays open.
        // SessionElapsed with freeze minutes must match HoursCalc at freeze, not live.
        val nineAm = 9 * 60
        val freeze = 10 * 60 + 30
        val live = 12 * 60
        val entry = com.rmltd.workhourstracker.data.DailyEntry(
            dateEpochDay = today.toEpochDay(),
            hoursWorked = 0.0,
            weekStartEpochDay = today.toEpochDay(),
            clockInMinutes = nineAm,
            clockOutMinutes = null
        )
        val paused = SessionElapsed.todayDisplayHours(entry, live, pauseFreezeMinutes = freeze)!!
        val liveHrs = SessionElapsed.todayDisplayHours(entry, live)!!
        assertTrue(paused < liveHrs)
        assertTrue(SessionElapsed.withinTolerance(paused, HoursCalc.hoursWorked(nineAm, freeze)))
        // Still OPEN classification — not closed
        assertEquals(
            com.rmltd.workhourstracker.data.ClockDayState.Kind.OPEN,
            com.rmltd.workhourstracker.data.ClockDayState.classify(
                entry.clockInMinutes, entry.clockOutMinutes, entry.hoursWorked
            )
        )
        assertFalse(entry.clockOutMinutes != null)
    }
}
