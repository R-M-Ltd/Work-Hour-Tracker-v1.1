package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoursCalcTest {

    @Test
    fun dayShift_noLunch() {
        // 9:00–17:00 = 8.00h
        val w = HoursCalc.worked(9 * 60, 17 * 60)
        assertEquals(8.00, w.hours, 0.001)
        assertFalse(w.overnight)
        assertFalse(w.lunchApplied)
    }

    @Test
    fun dayShift_withLunch() {
        // 8:00–17:00 minus 12:00–12:30 = 8.50h
        val w = HoursCalc.worked(8 * 60, 17 * 60, 12 * 60, 12 * 60 + 30)
        assertEquals(8.50, w.hours, 0.001)
        assertTrue(w.lunchApplied)
        assertFalse(w.overnight)
    }

    @Test
    fun overnight_crossesMidnight() {
        // 22:00–06:00 = 8.00h overnight
        val w = HoursCalc.worked(22 * 60, 6 * 60)
        assertEquals(8.00, w.hours, 0.001)
        assertTrue(w.overnight)
    }

    @Test
    fun equalWallClock_meansFullDay_whenFlagged() {
        // Home overnight finish with matching wall times → 24.00h
        val w = HoursCalc.worked(22 * 60, 22 * 60, equalOutMeansFullDay = true)
        assertEquals(24.00, w.hours, 0.001)
        assertTrue(w.overnight)
        assertTrue(HoursCalc.isOvernight(22 * 60, 22 * 60, equalOutMeansFullDay = true))
    }

    @Test
    fun equalWallClock_midnight_meansFullDay_whenFlagged() {
        val w = HoursCalc.worked(0, 0, equalOutMeansFullDay = true)
        assertEquals(24.00, w.hours, 0.001)
        assertTrue(w.overnight)
    }

    @Test
    fun equalWallClock_fullDay_withLunch() {
        // 22:00→22:00 (24h) minus lunch 02:00–02:30 = 23.50h
        val w = HoursCalc.worked(
            22 * 60,
            22 * 60,
            lunchOutMinutes = 2 * 60,
            lunchInMinutes = 2 * 60 + 30,
            equalOutMeansFullDay = true
        )
        assertEquals(23.50, w.hours, 0.001)
        assertTrue(w.lunchApplied)
        assertTrue(w.overnight)
    }

    @Test
    fun equalWallClock_withoutFlag_isNotOvernight() {
        assertFalse(HoursCalc.isOvernight(9 * 60, 9 * 60))
        val w = HoursCalc.worked(9 * 60, 9 * 60)
        assertEquals(0.00, w.hours, 0.001)
        assertFalse(w.overnight)
    }

    @Test
    fun hoursWorked_delegatesToWorked() {
        assertEquals(
            8.00,
            HoursCalc.hoursWorked(9 * 60, 17 * 60),
            0.001
        )
        assertEquals(
            24.00,
            HoursCalc.hoursWorked(22 * 60, 22 * 60, equalOutMeansFullDay = true),
            0.001
        )
    }

    @Test
    fun durationMinutes_overnight() {
        assertEquals(8 * 60, HoursCalc.durationMinutes(22 * 60, 6 * 60))
        assertEquals(8 * 60, HoursCalc.durationMinutes(9 * 60, 17 * 60))
    }

    @Test
    fun overnight_withLunchAfterMidnight() {
        // 22:00–06:00 minus 01:00–01:30 = 7.50h
        val w = HoursCalc.worked(22 * 60, 6 * 60, 1 * 60, 1 * 60 + 30)
        assertEquals(7.50, w.hours, 0.001)
        assertTrue(w.lunchApplied)
        assertTrue(w.overnight)
    }

    @Test
    fun partialLunch_ignored() {
        val w = HoursCalc.worked(9 * 60, 17 * 60, 12 * 60, null)
        assertEquals(8.00, w.hours, 0.001)
        assertFalse(w.lunchApplied)
    }

    @Test
    fun lunchOutsideShift_ignored() {
        // Lunch entirely before clock-in
        val before = HoursCalc.worked(9 * 60, 17 * 60, 7 * 60, 7 * 60 + 30)
        assertEquals(8.00, before.hours, 0.001)
        assertFalse(before.lunchApplied)
        // Lunch entirely after clock-out
        val after = HoursCalc.worked(9 * 60, 17 * 60, 18 * 60, 18 * 60 + 30)
        assertEquals(8.00, after.hours, 0.001)
        assertFalse(after.lunchApplied)
    }

    @Test
    fun roundsToHundredths() {
        // 8:00–16:07 = 8h 7m = 8.1166… → 8.12h
        val w = HoursCalc.worked(8 * 60, 16 * 60 + 7)
        assertEquals(8.12, w.hours, 0.001)
    }

    @Test
    fun formatHours_usLocaleHundredths() {
        assertEquals("8.00h", HoursCalc.formatHours(8.0))
        assertEquals("23.50h", HoursCalc.formatHours(23.5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMinutesOutOfRange() {
        HoursCalc.worked(-1, 17 * 60)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMinutesAtOrAboveDay() {
        HoursCalc.durationMinutes(0, HoursCalc.MINUTES_PER_DAY)
    }


    @Test(expected = IllegalArgumentException::class)
    fun rejectsIsOvernightOutOfRange() {
        HoursCalc.isOvernight(0, HoursCalc.MINUTES_PER_DAY)
    }

}
