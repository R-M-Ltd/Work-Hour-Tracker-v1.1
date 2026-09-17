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
        assertTrue(HoursCalc.isOvernight(22 * 60, 22 * 60, equalOutMeansFullDay = true))
    }

    @Test
    fun equalWallClock_withoutFlag_isNotOvernight() {
        assertFalse(HoursCalc.isOvernight(9 * 60, 9 * 60))
        val w = HoursCalc.worked(9 * 60, 9 * 60)
        assertEquals(0.00, w.hours, 0.001)
    }

    @Test
    fun partialLunch_ignored() {
        val w = HoursCalc.worked(9 * 60, 17 * 60, 12 * 60, null)
        assertEquals(8.00, w.hours, 0.001)
        assertFalse(w.lunchApplied)
    }

    @Test
    fun roundsToHundredths() {
        // 8:00–16:07 = 8h 7m = 8.1166… → 8.12h
        val w = HoursCalc.worked(8 * 60, 16 * 60 + 7)
        assertEquals(8.12, w.hours, 0.001)
    }
}
