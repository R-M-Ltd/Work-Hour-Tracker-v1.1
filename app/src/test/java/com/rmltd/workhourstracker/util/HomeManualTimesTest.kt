package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeManualTimesTest {

    private val nineAm = 9 * 60
    private val fivePm = 17 * 60
    private val tenPm = 22 * 60
    private val sixAm = 6 * 60
    private val noon = 12 * 60
    private val halfPastNoon = 12 * 60 + 30

    @Test
    fun canSave_requiresBothDistinct() {
        assertFalse(HomeManualTimes.canSave(null, null))
        assertFalse(HomeManualTimes.canSave(nineAm, null))
        assertFalse(HomeManualTimes.canSave(null, fivePm))
        assertFalse(HomeManualTimes.canSave(nineAm, nineAm))
        assertTrue(HomeManualTimes.canSave(nineAm, fivePm))
        assertTrue(HomeManualTimes.canSave(tenPm, sixAm))
    }

    @Test
    fun needsOvernightConfirm_whenOutBeforeIn() {
        assertFalse(HomeManualTimes.needsOvernightConfirm(nineAm, fivePm))
        assertTrue(HomeManualTimes.needsOvernightConfirm(tenPm, sixAm))
        assertFalse(HomeManualTimes.needsOvernightConfirm(nineAm, nineAm))
    }

    @Test
    fun lunchToPreserve_keepsCompletePairOnly() {
        assertEquals(noon to halfPastNoon, HomeManualTimes.lunchToPreserve(noon, halfPastNoon))
        assertEquals(null to null, HomeManualTimes.lunchToPreserve(noon, null))
        assertEquals(null to null, HomeManualTimes.lunchToPreserve(null, halfPastNoon))
        assertEquals(null to null, HomeManualTimes.lunchToPreserve(null, null))
    }

    @Test
    fun defaultPickerMinutes_matchEntryDefaults() {
        assertEquals(8 * 60, HomeManualTimes.defaultPickerMinutes(isClockIn = true))
        assertEquals(17 * 60, HomeManualTimes.defaultPickerMinutes(isClockIn = false))
    }

    @Test
    fun canSave_midnightAndLastMinute_boundaries() {
        // Distinct wall times at day edges are savable (overnight or near-full day).
        assertTrue(HomeManualTimes.canSave(0, 1))
        assertTrue(HomeManualTimes.canSave(23 * 60 + 59, 0))
        assertFalse(HomeManualTimes.canSave(0, 0))
        assertFalse(HomeManualTimes.canSave(23 * 60 + 59, 23 * 60 + 59))
    }

    @Test
    fun needsOvernightConfirm_oneMinutePastMidnight() {
        assertTrue(HomeManualTimes.needsOvernightConfirm(23 * 60 + 59, 0))
        assertFalse(HomeManualTimes.needsOvernightConfirm(0, 23 * 60 + 59))
        // Equal wall: Home manual save rejects via canSave; confirm stays false
        // (equalOutMeansFullDay is clock-out path only, not Home pickers).
        assertFalse(HomeManualTimes.needsOvernightConfirm(22 * 60, 22 * 60))
    }

    @Test
    fun lunchToPreserve_keepsEqualPair_doesNotInventPartial() {
        // Both present (even if equal minutes) are preserved; caller/HoursCalc
        // decides whether lunch applies. Partial sides still clear.
        val same = 12 * 60
        assertEquals(same to same, HomeManualTimes.lunchToPreserve(same, same))
        assertEquals(null to null, HomeManualTimes.lunchToPreserve(same, null))
    }
}
