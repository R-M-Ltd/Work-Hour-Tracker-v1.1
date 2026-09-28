package com.rmltd.workhourstracker.util

import com.rmltd.workhourstracker.data.DailyEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionElapsedTest {

    private val day = 20_000L
    private val nineAm = 9 * 60
    private val noon = 12 * 60
    private val fivePm = 17 * 60

    private fun entry(
        inM: Int? = null,
        outM: Int? = null,
        hours: Double = 0.0,
        noLunch: Boolean = false,
        breakMins: Int? = null
    ) = DailyEntry(
        dateEpochDay = day,
        hoursWorked = hours,
        weekStartEpochDay = day,
        clockInMinutes = inM,
        clockOutMinutes = outM,
        breakDurationMinutes = breakMins,
        noLunchTaken = noLunch
    )

    @Test
    fun notClockedIn_returnsNull() {
        assertNull(SessionElapsed.todayDisplayHours(null, noon))
        assertNull(SessionElapsed.todayDisplayHours(entry(), noon))
    }

    @Test
    fun openPunch_liveElapsedMatchesHoursCalc() {
        val e = entry(inM = nineAm)
        val live = SessionElapsed.todayDisplayHours(e, noon)!!
        val expected = HoursCalc.hoursWorked(nineAm, noon)
        assertTrue(SessionElapsed.withinTolerance(live, expected))
        assertEquals(3.0, live, SessionElapsed.TOLERANCE_HOURS)
    }

    @Test
    fun openPunch_respectsNoLunchAndBreak() {
        val withBreak = entry(inM = nineAm, breakMins = 30)
        val noLunch = entry(inM = nineAm, noLunch = true, breakMins = 30)
        val a = SessionElapsed.todayDisplayHours(withBreak, fivePm)!!
        val b = SessionElapsed.todayDisplayHours(noLunch, fivePm)!!
        assertTrue(b > a)
        assertTrue(SessionElapsed.withinTolerance(a, HoursCalc.hoursWorked(nineAm, fivePm, breakDurationMinutes = 30)))
        assertTrue(SessionElapsed.withinTolerance(b, HoursCalc.hoursWorked(nineAm, fivePm, noLunchTaken = true, breakDurationMinutes = 30)))
    }

    @Test
    fun closedDay_usesPersistedHours() {
        val e = entry(inM = nineAm, outM = fivePm, hours = 8.0)
        assertEquals(8.0, SessionElapsed.todayDisplayHours(e, noon)!!, 0.0)
    }

    @Test
    fun weekActual_includesLiveOpenToday() {
        val closed = entry(inM = nineAm, outM = fivePm, hours = 8.0).copy(dateEpochDay = day - 1)
        val open = entry(inM = nineAm)
        val week = SessionElapsed.weekActualHours(listOf(closed, open), day, noon)
        // 8.0 + 3.0
        assertTrue(SessionElapsed.withinTolerance(week, 11.0))
    }

    @Test
    fun goalsAndWidgetAgreeWithinTolerance() {
        val open = entry(inM = nineAm)
        val widgetToday = SessionElapsed.todayDisplayHours(open, noon)!!
        val goalsWeek = SessionElapsed.weekActualHours(listOf(open), day, noon)
        assertTrue(SessionElapsed.withinTolerance(widgetToday, goalsWeek))
    }
}
