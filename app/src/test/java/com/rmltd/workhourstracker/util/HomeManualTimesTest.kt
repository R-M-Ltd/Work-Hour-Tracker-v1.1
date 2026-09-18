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
}
