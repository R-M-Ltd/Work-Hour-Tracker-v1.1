package com.rmltd.workhourstracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure unit coverage for [WorkHoursRepository.deriveHomeClockUi]
 * (Empty / Open / Closed / overnight-pending / legacy).
 *
 * clockInNow / clockOutNow transitions still need instrumented or in-memory Room
 * tests (DAO + mutex + upsert paths).
 */
class HomeClockUiTest {

    private val today = LocalDate.of(2026, 9, 17)
    private val yesterday = today.minusDays(1)

    private fun entry(
        date: LocalDate,
        clockIn: Int? = null,
        clockOut: Int? = null,
        hours: Double = 0.0
    ) = DailyEntry(
        dateEpochDay = date.toEpochDay(),
        hoursWorked = hours,
        weekStartEpochDay = date.toEpochDay(), // irrelevant for UI derive
        clockInMinutes = clockIn,
        clockOutMinutes = clockOut
    )

    @Test
    fun empty_enablesClockIn_disablesClockOut() {
        val ui = WorkHoursRepository.deriveHomeClockUi(null, null)
        assertTrue(ui.clockInEnabled)
        assertFalse(ui.clockOutEnabled)
        assertFalse(ui.overnightPending)
        assertNull(ui.openOvernightDate)
    }

    @Test
    fun openToday_disablesClockIn_enablesClockOut() {
        val ui = WorkHoursRepository.deriveHomeClockUi(
            entry(today, clockIn = 9 * 60),
            null
        )
        assertFalse(ui.clockInEnabled)
        assertTrue(ui.clockOutEnabled)
        assertFalse(ui.overnightPending)
    }

    @Test
    fun closedToday_disablesBoth() {
        val ui = WorkHoursRepository.deriveHomeClockUi(
            entry(today, clockIn = 9 * 60, clockOut = 17 * 60, hours = 8.0),
            null
        )
        assertFalse(ui.clockInEnabled)
        assertFalse(ui.clockOutEnabled)
        assertFalse(ui.overnightPending)
    }

    @Test
    fun overnightPending_enablesBoth_andExposesYesterday() {
        // Empty today + yesterday open → clock-in stays on (resolve dialog); clock-out on
        val ui = WorkHoursRepository.deriveHomeClockUi(
            null,
            entry(yesterday, clockIn = 22 * 60)
        )
        assertTrue(ui.clockInEnabled)
        assertTrue(ui.clockOutEnabled)
        assertTrue(ui.overnightPending)
        assertEquals(yesterday, ui.openOvernightDate)
    }

    @Test
    fun overnightPending_withOpenToday_prefersTodayOpen() {
        // Unusual: both open — todayOpen wins for clock-in disabled
        val ui = WorkHoursRepository.deriveHomeClockUi(
            entry(today, clockIn = 8 * 60),
            entry(yesterday, clockIn = 22 * 60)
        )
        assertFalse(ui.clockInEnabled)
        assertTrue(ui.clockOutEnabled)
        assertTrue(ui.overnightPending)
        assertEquals(yesterday, ui.openOvernightDate)
    }

    @Test
    fun legacyHoursOnly_treatedAsClosed() {
        val ui = WorkHoursRepository.deriveHomeClockUi(
            entry(today, hours = 8.0),
            null
        )
        assertFalse(ui.clockInEnabled)
        assertFalse(ui.clockOutEnabled)
        assertFalse(ui.overnightPending)
    }

    @Test
    fun emptyRowZeroHours_stillEmpty() {
        // Upserted-then-cleared style: row exists but no clocks and 0h → Empty
        val ui = WorkHoursRepository.deriveHomeClockUi(
            entry(today, hours = 0.0),
            null
        )
        assertTrue(ui.clockInEnabled)
        assertFalse(ui.clockOutEnabled)
    }

    @Test
    fun yesterdayClosed_doesNotArmOvernight() {
        val ui = WorkHoursRepository.deriveHomeClockUi(
            null,
            entry(yesterday, clockIn = 22 * 60, clockOut = 6 * 60, hours = 8.0)
        )
        assertTrue(ui.clockInEnabled)
        assertFalse(ui.clockOutEnabled)
        assertFalse(ui.overnightPending)
        assertNull(ui.openOvernightDate)
    }
}
