package com.rmltd.workhourstracker.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentTest {

    private val nineAm = 9 * 60
    private val fivePm = 17 * 60
    private val noon = 12 * 60

    @Test
    fun empty_notClockedIn_hidesToday() {
        val d = WidgetContent.build(
            todayIn = null,
            todayOut = null,
            todayHoursWorked = 0.0,
            overnightPending = false,
            weekHours = 12.5,
            weekGoalHours = 40.0,
            nowMinutes = noon
        )
        assertEquals("Not clocked in", d.statusLine)
        assertNull(d.todayLine)
        assertFalse(d.openSession)
        assertEquals("Week 12.50h / 40.00h", d.weekLine)
    }

    @Test
    fun open_showsSinceAndTodayRunning() {
        val d = WidgetContent.build(
            todayIn = nineAm,
            todayOut = null,
            todayHoursWorked = 0.0,
            overnightPending = false,
            weekHours = 0.0,
            weekGoalHours = 40.0,
            nowMinutes = noon
        )
        assertTrue(d.statusLine.startsWith("Clocked in since"))
        assertTrue(d.statusLine.contains("9:00"))
        assertEquals("Today: 3.00h", d.todayLine)
        assertTrue(d.openSession)
    }

    @Test
    fun closed_showsCompletedWithHoursAndToday() {
        val d = WidgetContent.build(
            todayIn = nineAm,
            todayOut = fivePm,
            todayHoursWorked = 8.0,
            overnightPending = false,
            weekHours = 8.0,
            weekGoalHours = 40.0,
            nowMinutes = noon
        )
        assertTrue(d.statusLine.startsWith("Completed"))
        assertTrue(d.statusLine.contains("8.00h"))
        assertEquals("Today: 8.00h", d.todayLine)
        assertFalse(d.openSession)
        assertEquals("Week 8.00h / 40.00h", d.weekLine)
    }

    @Test
    fun legacyClosed_showsCompletedHoursOnly() {
        val d = WidgetContent.build(
            todayIn = null,
            todayOut = null,
            todayHoursWorked = 7.5,
            overnightPending = false,
            weekHours = 7.5,
            weekGoalHours = 40.0,
            nowMinutes = noon
        )
        assertEquals("Completed · 7.50h", d.statusLine)
        assertEquals("Today: 7.50h", d.todayLine)
    }

    @Test
    fun overnight_emptyToday_promptsOpenApp_hidesToday() {
        val d = WidgetContent.build(
            todayIn = null,
            todayOut = null,
            todayHoursWorked = 0.0,
            overnightPending = true,
            weekHours = 20.0,
            weekGoalHours = 40.0,
            nowMinutes = noon
        )
        assertEquals(WidgetContent.OVERNIGHT_OPEN_STATUS, d.statusLine)
        assertFalse(d.statusLine.contains("tap to resolve"))
        assertTrue(d.statusLine.contains("open app"))
        assertNull(d.todayLine)
    }

    @Test
    fun overnight_plusOpenToday_showsClockedInNotOvernightCopy() {
        val d = WidgetContent.build(
            todayIn = nineAm,
            todayOut = null,
            todayHoursWorked = 0.0,
            overnightPending = true,
            weekHours = 20.0,
            weekGoalHours = 40.0,
            nowMinutes = noon
        )
        assertTrue(d.statusLine.startsWith("Clocked in since"))
        assertFalse(d.statusLine.contains("Overnight"))
        assertEquals("Today: 3.00h", d.todayLine)
    }

    @Test
    fun overnight_plusClosedToday_showsCompleted() {
        val d = WidgetContent.build(
            todayIn = nineAm,
            todayOut = fivePm,
            todayHoursWorked = 8.0,
            overnightPending = true,
            weekHours = 28.0,
            weekGoalHours = 40.0,
            nowMinutes = noon
        )
        assertTrue(d.statusLine.startsWith("Completed"))
        assertFalse(d.statusLine.contains("Overnight"))
    }

    @Test
    fun clockAndWeek_useUsLocaleStyle() {
        val d = WidgetContent.build(
            todayIn = 0,
            todayOut = null,
            todayHoursWorked = 0.0,
            overnightPending = false,
            weekHours = 1.5,
            weekGoalHours = 40.0,
            nowMinutes = 90
        )
        assertTrue(d.statusLine.contains("12:00 AM"))
        assertEquals("Today: 1.50h", d.todayLine)
        assertEquals("Week 1.50h / 40.00h", d.weekLine)
    }
}
