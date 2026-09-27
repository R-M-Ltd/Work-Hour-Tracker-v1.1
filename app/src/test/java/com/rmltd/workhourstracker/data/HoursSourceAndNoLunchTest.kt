package com.rmltd.workhourstracker.data

import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.util.ZeroTimeNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoursSourceAndNoLunchTest {

    @Test
    fun hoursSource_migrationDefaultIsClock() {
        assertEquals(HoursSource.CLOCK, HoursSource.fromStorage(null))
        assertEquals(HoursSource.CLOCK, HoursSource.fromStorage(""))
        assertEquals(HoursSource.CLOCK, HoursSource.fromStorage("CLOCK"))
        assertEquals(HoursSource.TYPED, HoursSource.fromStorage("TYPED"))
        val row = DailyEntry(
            dateEpochDay = 1,
            hoursWorked = 8.0,
            weekStartEpochDay = 1
        )
        assertEquals(HoursSource.CLOCK.name, row.hoursSource)
        assertFalse(row.noLunchTaken)
    }

    @Test
    fun typedWins_concept_persistTypedNotClockDerived() {
        // Clocks say 9–17 = 8h; typed 7.5 must win on Save (engine stores typed).
        val clockDerived = HoursCalc.hoursWorked(9 * 60, 17 * 60)
        assertEquals(8.0, clockDerived, 0.001)
        val typed = 7.5
        // Repository path: when typedHours != null, hoursWorked = typed
        assertTrue(typed != clockDerived)
        val saved = DailyEntry(
            dateEpochDay = 1,
            hoursWorked = typed,
            weekStartEpochDay = 1,
            clockInMinutes = 9 * 60,
            clockOutMinutes = 17 * 60,
            hoursSource = HoursSource.TYPED.name
        )
        assertEquals(7.5, saved.hoursWorked, 0.0)
        assertEquals(HoursSource.TYPED, saved.hoursSourceEnum())
        assertEquals(9 * 60, saved.clockInMinutes)
    }

    @Test
    fun typedZero_needsReason() {
        assertTrue(
            ZeroTimeNote.needsZeroHoursReason(null, null, null, typedHours = 0.0)
        )
        assertFalse(
            ZeroTimeNote.needsZeroHoursReason(null, null, null, typedHours = 8.0)
        )
        // Equal in/out still alternate path
        assertTrue(
            ZeroTimeNote.needsZeroHoursReason(9 * 60, 9 * 60, 0.0)
        )
    }

    @Test
    fun noLunchTaken_skipsBreakSubtract() {
        // 9–17 with 1h lunch → 7h normally; with noLunch → 8h
        val withLunch = HoursCalc.hoursWorked(
            9 * 60, 17 * 60, 12 * 60, 13 * 60
        )
        assertEquals(7.0, withLunch, 0.001)
        val noLunch = HoursCalc.hoursWorked(
            9 * 60, 17 * 60, 12 * 60, 13 * 60, noLunchTaken = true
        )
        assertEquals(8.0, noLunch, 0.001)
        val withDuration = HoursCalc.hoursWorked(
            9 * 60, 17 * 60, breakDurationMinutes = 30
        )
        assertEquals(7.5, withDuration, 0.001)
        val noLunchDur = HoursCalc.hoursWorked(
            9 * 60, 17 * 60, breakDurationMinutes = 30, noLunchTaken = true
        )
        assertEquals(8.0, noLunchDur, 0.001)
    }

    @Test
    fun hasPersistedHours_typedAndClosed() {
        val typed = DailyEntry(
            dateEpochDay = 1,
            hoursWorked = 0.0,
            weekStartEpochDay = 1,
            hoursSource = HoursSource.TYPED.name
        )
        assertTrue(typed.hasPersistedHours())
        val open = DailyEntry(
            dateEpochDay = 1,
            hoursWorked = 0.0,
            weekStartEpochDay = 1,
            clockInMinutes = 9 * 60,
            clockOutMinutes = null
        )
        assertFalse(open.hasPersistedHours())
        val closed = DailyEntry(
            dateEpochDay = 1,
            hoursWorked = 8.0,
            weekStartEpochDay = 1,
            clockInMinutes = 9 * 60,
            clockOutMinutes = 17 * 60
        )
        assertTrue(closed.hasPersistedHours())
    }

    @Test
    fun homeManualTimes_canSave_stillRequiresBothClocks() {
        assertFalse(com.rmltd.workhourstracker.util.HomeManualTimes.canSave(null, null))
        assertTrue(com.rmltd.workhourstracker.util.HomeManualTimes.canSave(1, 2))
    }
}
