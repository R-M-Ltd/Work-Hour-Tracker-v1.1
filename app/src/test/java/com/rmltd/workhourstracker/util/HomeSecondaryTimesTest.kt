package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSecondaryTimesTest {

    private val eightAm = 8 * 60
    private val noon = 12 * 60

    @Test
    fun pureEmpty_hidesChips() {
        assertFalse(
            HomeSecondaryTimes.shouldShow(
                openSession = false,
                overnightPending = false,
                inMinutes = null,
                outMinutes = null
            )
        )
    }

    @Test
    fun idlePartial_closedOrDraft_showsChips() {
        assertTrue(
            HomeSecondaryTimes.shouldShow(
                openSession = false,
                overnightPending = false,
                inMinutes = eightAm,
                outMinutes = noon
            )
        )
        assertTrue(
            HomeSecondaryTimes.shouldShow(
                openSession = false,
                overnightPending = false,
                inMinutes = eightAm,
                outMinutes = null
            )
        )
    }

    @Test
    fun runningAndPaused_showChips() {
        assertTrue(
            HomeSecondaryTimes.shouldShow(
                openSession = true,
                overnightPending = false,
                inMinutes = eightAm,
                outMinutes = null
            )
        )
    }

    @Test
    fun overnightPending_hidesChips() {
        assertFalse(
            HomeSecondaryTimes.shouldShow(
                openSession = false,
                overnightPending = true,
                inMinutes = eightAm,
                outMinutes = null
            )
        )
    }

    @Test
    fun labels_openUsesSinceAndEmptyStop() {
        assertEquals(
            "Since 8:00 AM",
            HomeSecondaryTimes.startChipLabel(openSession = true, inMinutes = eightAm)
        )
        assertEquals(
            "Stop —",
            HomeSecondaryTimes.stopChipLabel(openSession = true, outMinutes = null)
        )
    }

    @Test
    fun labels_idlePartialUsesStartStopValues() {
        assertEquals(
            "Start 8:00 AM",
            HomeSecondaryTimes.startChipLabel(openSession = false, inMinutes = eightAm)
        )
        assertEquals(
            "Stop 12:00 PM",
            HomeSecondaryTimes.stopChipLabel(openSession = false, outMinutes = noon)
        )
    }

    @Test
    fun widgetStatus_runningPausedClosed() {
        assertEquals(
            "Start 8:00 AM · Stop —",
            HomeSecondaryTimes.widgetStatusLine(
                open = true, paused = false, inMinutes = eightAm, outMinutes = null
            )
        )
        assertEquals(
            "Paused · since 8:00 AM",
            HomeSecondaryTimes.widgetStatusLine(
                open = true, paused = true, inMinutes = eightAm, outMinutes = null
            )
        )
        assertEquals(
            "Start 8:00 AM · Stop 12:00 PM",
            HomeSecondaryTimes.widgetStatusLine(
                open = false, paused = false, inMinutes = eightAm, outMinutes = noon
            )
        )
    }
}
