package com.rmltd.workhourstracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure JVM coverage for [ClockDayState]: day Kind matrix + clockIn/clockOut
 * transition decisions + Home UI derive.
 *
 * Room writes (mutex / upsert / DAO) still need instrumented or in-memory Room
 * tests via local SDK / gradlew.
 */
class ClockDayStateTest {

    private val today = LocalDate.of(2026, 9, 17)
    private val yesterday = today.minusDays(1)
    private val nineAm = 9 * 60
    private val fivePm = 17 * 60
    private val tenPm = 22 * 60
    private val sixAm = 6 * 60

    private fun entry(
        date: LocalDate,
        clockIn: Int? = null,
        clockOut: Int? = null,
        hours: Double = 0.0
    ) = DailyEntry(
        dateEpochDay = date.toEpochDay(),
        hoursWorked = hours,
        weekStartEpochDay = date.toEpochDay(),
        clockInMinutes = clockIn,
        clockOutMinutes = clockOut
    )

    // --- classify ---

    @Test
    fun classify_empty_nullAndZeroHours() {
        assertEquals(ClockDayState.Kind.EMPTY, ClockDayState.classify(null, null, 0.0))
    }

    @Test
    fun classify_open() {
        assertEquals(ClockDayState.Kind.OPEN, ClockDayState.classify(nineAm, null, 0.0))
    }

    @Test
    fun classify_closed() {
        assertEquals(ClockDayState.Kind.CLOSED, ClockDayState.classify(nineAm, fivePm, 8.0))
    }

    @Test
    fun classify_legacyClosed() {
        assertEquals(ClockDayState.Kind.LEGACY_CLOSED, ClockDayState.classify(null, null, 8.0))
    }

    @Test
    fun isOvernightOpen_onlyWhenInWithoutOut() {
        assertTrue(ClockDayState.isOvernightOpen(tenPm, null))
        assertFalse(ClockDayState.isOvernightOpen(tenPm, sixAm))
        assertFalse(ClockDayState.isOvernightOpen(null, null))
    }

    // --- decideClockIn ---

    @Test
    fun clockIn_empty_starts() {
        assertEquals(
            ClockInResult.STARTED,
            ClockDayState.decideClockIn(null, null, 0.0, null, null)
        )
    }

    @Test
    fun clockIn_open_alreadyOpen() {
        assertEquals(
            ClockInResult.ALREADY_OPEN,
            ClockDayState.decideClockIn(nineAm, null, 0.0, null, null)
        )
    }

    @Test
    fun clockIn_closed_alreadyClosed() {
        assertEquals(
            ClockInResult.ALREADY_CLOSED,
            ClockDayState.decideClockIn(nineAm, fivePm, 8.0, null, null)
        )
    }

    @Test
    fun clockIn_legacy_alreadyClosed() {
        assertEquals(
            ClockInResult.ALREADY_CLOSED,
            ClockDayState.decideClockIn(null, null, 8.0, null, null)
        )
    }

    @Test
    fun clockIn_emptyWithOvernight_blocked() {
        assertEquals(
            ClockInResult.BLOCKED_OVERNIGHT,
            ClockDayState.decideClockIn(null, null, 0.0, tenPm, null)
        )
    }

    @Test
    fun clockIn_emptyWithYesterdayClosed_starts() {
        assertEquals(
            ClockInResult.STARTED,
            ClockDayState.decideClockIn(null, null, 0.0, tenPm, sixAm)
        )
    }

    @Test
    fun clockIn_openIgnoresOvernight_alreadyOpen() {
        // Today open wins even if yesterday also open
        assertEquals(
            ClockInResult.ALREADY_OPEN,
            ClockDayState.decideClockIn(nineAm, null, 0.0, tenPm, null)
        )
    }

    // --- decideClockOut ---

    @Test
    fun clockOut_openToday_success() {
        assertEquals(
            ClockOutResult.SUCCESS,
            ClockDayState.decideClockOut(nineAm, null, null, null, fivePm)
        )
    }

    @Test
    fun clockOut_openToday_equalMinutes_failed() {
        assertEquals(
            ClockOutResult.FAILED,
            ClockDayState.decideClockOut(nineAm, null, null, null, nineAm)
        )
    }

    @Test
    fun clockOut_closedToday_alreadyClosed() {
        assertEquals(
            ClockOutResult.ALREADY_CLOSED,
            ClockDayState.decideClockOut(nineAm, fivePm, null, null, 18 * 60)
        )
    }

    @Test
    fun clockOut_emptyWithOvernight_successOvernight() {
        assertEquals(
            ClockOutResult.SUCCESS_OVERNIGHT,
            ClockDayState.decideClockOut(null, null, tenPm, null, sixAm)
        )
    }

    @Test
    fun clockOut_emptyWithOvernight_equalWall_stillOvernight() {
        // Equal wall OK for overnight finish (24h); decision is SUCCESS_OVERNIGHT
        assertEquals(
            ClockOutResult.SUCCESS_OVERNIGHT,
            ClockDayState.decideClockOut(null, null, tenPm, null, tenPm)
        )
    }

    @Test
    fun clockOut_emptyNoOvernight_failed() {
        assertEquals(
            ClockOutResult.FAILED,
            ClockDayState.decideClockOut(null, null, null, null, fivePm)
        )
    }

    @Test
    fun clockOut_emptyYesterdayClosed_failed() {
        assertEquals(
            ClockOutResult.FAILED,
            ClockDayState.decideClockOut(null, null, tenPm, sixAm, fivePm)
        )
    }

    @Test
    fun clockOut_openToday_prefersTodayOverOvernight() {
        assertEquals(
            ClockOutResult.SUCCESS,
            ClockDayState.decideClockOut(nineAm, null, tenPm, null, fivePm)
        )
    }

    // --- deriveHomeClockUi (entry overload + field overload) ---

    @Test
    fun ui_empty_enablesClockIn_disablesClockOut() {
        val ui = ClockDayState.deriveHomeClockUi(null, null)
        assertTrue(ui.clockInEnabled)
        assertFalse(ui.clockOutEnabled)
        assertFalse(ui.overnightPending)
        assertNull(ui.openOvernightDate)
    }

    @Test
    fun ui_openToday_disablesClockIn_enablesClockOut() {
        val ui = ClockDayState.deriveHomeClockUi(entry(today, clockIn = nineAm), null)
        assertFalse(ui.clockInEnabled)
        assertTrue(ui.clockOutEnabled)
        assertFalse(ui.overnightPending)
    }

    @Test
    fun ui_closedToday_disablesBoth() {
        val ui = ClockDayState.deriveHomeClockUi(
            entry(today, clockIn = nineAm, clockOut = fivePm, hours = 8.0),
            null
        )
        assertFalse(ui.clockInEnabled)
        assertFalse(ui.clockOutEnabled)
        assertFalse(ui.overnightPending)
    }

    @Test
    fun ui_overnightPending_enablesBoth_andExposesYesterday() {
        val ui = ClockDayState.deriveHomeClockUi(
            null,
            entry(yesterday, clockIn = tenPm)
        )
        assertTrue(ui.clockInEnabled)
        assertTrue(ui.clockOutEnabled)
        assertTrue(ui.overnightPending)
        assertEquals(yesterday, ui.openOvernightDate)
    }

    @Test
    fun ui_overnightPending_withOpenToday_prefersTodayOpen() {
        val ui = ClockDayState.deriveHomeClockUi(
            entry(today, clockIn = 8 * 60),
            entry(yesterday, clockIn = tenPm)
        )
        assertFalse(ui.clockInEnabled)
        assertTrue(ui.clockOutEnabled)
        assertTrue(ui.overnightPending)
        assertEquals(yesterday, ui.openOvernightDate)
    }

    @Test
    fun ui_legacyHoursOnly_treatedAsClosed() {
        val ui = ClockDayState.deriveHomeClockUi(entry(today, hours = 8.0), null)
        assertFalse(ui.clockInEnabled)
        assertFalse(ui.clockOutEnabled)
        assertFalse(ui.overnightPending)
    }

    @Test
    fun ui_emptyRowZeroHours_stillEmpty() {
        val ui = ClockDayState.deriveHomeClockUi(entry(today, hours = 0.0), null)
        assertTrue(ui.clockInEnabled)
        assertFalse(ui.clockOutEnabled)
    }

    @Test
    fun ui_yesterdayClosed_doesNotArmOvernight() {
        val ui = ClockDayState.deriveHomeClockUi(
            null,
            entry(yesterday, clockIn = tenPm, clockOut = sixAm, hours = 8.0)
        )
        assertTrue(ui.clockInEnabled)
        assertFalse(ui.clockOutEnabled)
        assertFalse(ui.overnightPending)
        assertNull(ui.openOvernightDate)
    }

    @Test
    fun ui_fieldOverload_matchesEntryOverload() {
        val fromEntry = ClockDayState.deriveHomeClockUi(
            entry(today, clockIn = nineAm),
            entry(yesterday, clockIn = tenPm)
        )
        val fromFields = ClockDayState.deriveHomeClockUi(
            todayIn = nineAm,
            todayOut = null,
            todayHoursWorked = 0.0,
            yesterdayIn = tenPm,
            yesterdayOut = null,
            yesterdayEpochDay = yesterday.toEpochDay()
        )
        assertEquals(fromEntry, fromFields)
    }

    @Test
    fun repositoryDelegate_matchesClockDayState() {
        val todayE = entry(today, clockIn = nineAm, clockOut = fivePm, hours = 8.0)
        assertEquals(
            ClockDayState.deriveHomeClockUi(todayE, null),
            WorkHoursRepository.deriveHomeClockUi(todayE, null)
        )
    }
}
