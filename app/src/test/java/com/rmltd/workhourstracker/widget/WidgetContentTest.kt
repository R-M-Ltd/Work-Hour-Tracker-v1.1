package com.rmltd.workhourstracker.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentTest {

    private val nineAm = 9 * 60
    private val fivePm = 17 * 60

    @Test
    fun empty_notClockedIn() {
        val d = WidgetContent.build(
            todayIn = null,
            todayOut = null,
            todayHoursWorked = 0.0,
            overnightPending = false,
            weekHours = 12.5,
            weekGoalHours = 40.0
        )
        assertEquals("Not clocked in", d.statusLine)
        assertEquals("Week 12.50h / 40.00h", d.weekLine)
    }

    @Test
    fun open_showsSince() {
        val d = WidgetContent.build(
            todayIn = nineAm,
            todayOut = null,
            todayHoursWorked = 0.0,
            overnightPending = false,
            weekHours = 0.0,
            weekGoalHours = 40.0
        )
        assertTrue(d.statusLine.startsWith("Clocked in since"))
        assertTrue(d.statusLine.contains("9:00"))
    }

    @Test
    fun closed_showsCompletedWithHours() {
        val d = WidgetContent.build(
            todayIn = nineAm,
            todayOut = fivePm,
            todayHoursWorked = 8.0,
            overnightPending = false,
            weekHours = 8.0,
            weekGoalHours = 40.0
        )
        assertTrue(d.statusLine.startsWith("Completed"))
        assertTrue(d.statusLine.contains("8.00h"))
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
            weekGoalHours = 40.0
        )
        assertEquals("Completed · 7.50h", d.statusLine)
    }

    @Test
    fun overnight_emptyToday_promptsResolve() {
        val d = WidgetContent.build(
            todayIn = null,
            todayOut = null,
            todayHoursWorked = 0.0,
            overnightPending = true,
            weekHours = 20.0,
            weekGoalHours = 40.0
        )
        assertEquals("Overnight open — tap to resolve", d.statusLine)
    }
}
