package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

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


    @Test
    fun lunchInBeforeOrEqualOut_ignored() {
        // Same lunch out/in → not usable
        val same = HoursCalc.worked(9 * 60, 17 * 60, 12 * 60, 12 * 60)
        assertEquals(8.00, same.hours, 0.001)
        assertFalse(same.lunchApplied)
        // lunch-in before lunch-out on timeline
        val inverted = HoursCalc.worked(9 * 60, 17 * 60, 13 * 60, 12 * 60)
        assertEquals(8.00, inverted.hours, 0.001)
        assertFalse(inverted.lunchApplied)
    }

    @Test
    fun lunchTouchingShiftBoundaries_ignored() {
        // lunchOut == clockIn → lunchOut <= clockIn
        val atIn = HoursCalc.worked(9 * 60, 17 * 60, 9 * 60, 9 * 60 + 30)
        assertEquals(8.00, atIn.hours, 0.001)
        assertFalse(atIn.lunchApplied)
        // lunchIn == clockOut → lunchIn >= outOnTimeline
        val atOut = HoursCalc.worked(9 * 60, 17 * 60, 16 * 60 + 30, 17 * 60)
        assertEquals(8.00, atOut.hours, 0.001)
        assertFalse(atOut.lunchApplied)
    }

    @Test
    fun partialLunch_onlyIn_ignored() {
        val w = HoursCalc.worked(9 * 60, 17 * 60, null, 12 * 60 + 30)
        assertEquals(8.00, w.hours, 0.001)
        assertFalse(w.lunchApplied)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLunchMinutesOutOfRange() {
        HoursCalc.worked(9 * 60, 17 * 60, -1, 12 * 60)
    }

    @Test
    fun overnight_lunchBeforeClockInWall_stillOnTimeline() {
        // 22:00–06:00; lunch 23:00–23:30 (same calendar evening) = 7.50h
        val w = HoursCalc.worked(22 * 60, 6 * 60, 23 * 60, 23 * 60 + 30)
        assertEquals(7.50, w.hours, 0.001)
        assertTrue(w.lunchApplied)
        assertTrue(w.overnight)
    }

    @Test
    fun formatRange_nullWhenEitherMissing() {
        assertNull(HoursCalc.formatRange(null, 17 * 60))
        assertNull(HoursCalc.formatRange(9 * 60, null))
        assertNull(HoursCalc.formatRange(null, null))
    }

    @Test
    fun formatClock_range_andDayLabel_usLocale() {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("9:00 AM", HoursCalc.formatClock(9 * 60))
            assertEquals("12:00 AM", HoursCalc.formatClock(0))
            assertEquals("12:30 PM", HoursCalc.formatClock(12 * 60 + 30))
            assertEquals("9:00 AM – 5:00 PM", HoursCalc.formatRange(9 * 60, 17 * 60))
            assertEquals(
                "9:00 AM – 5:00 PM",
                HoursCalc.formatDayLabel(9 * 60, 17 * 60, null, null)
            )
            assertEquals(
                "9:00 AM – 5:00 PM  ·  Break 12:00 PM – 12:30 PM",
                HoursCalc.formatDayLabel(9 * 60, 17 * 60, 12 * 60, 12 * 60 + 30)
            )
            assertNull(HoursCalc.formatDayLabel(null, 17 * 60, 12 * 60, 12 * 60 + 30))
            // Partial lunch → range only (no lunch segment)
            assertEquals(
                "9:00 AM – 5:00 PM",
                HoursCalc.formatDayLabel(9 * 60, 17 * 60, 12 * 60, null)
            )
        } finally {
            Locale.setDefault(prev)
        }
    }



    @Test
    fun durationMinutes_equalWall_isZero() {
        assertEquals(0, HoursCalc.durationMinutes(9 * 60, 9 * 60))
        assertEquals(0, HoursCalc.durationMinutes(0, 0))
    }

    @Test
    fun oneMinuteShift_andNearFullDayOvernight() {
        val minute = HoursCalc.worked(9 * 60, 9 * 60 + 1)
        assertEquals(0.02, minute.hours, 0.001)
        assertFalse(minute.overnight)
        // 23:59 → 00:00 = 1 minute overnight
        val overnightMinute = HoursCalc.worked(23 * 60 + 59, 0)
        assertEquals(0.02, overnightMinute.hours, 0.001)
        assertTrue(overnightMinute.overnight)
        // 00:00 → 23:59 = 23h 59m day shift
        val almostDay = HoursCalc.worked(0, 23 * 60 + 59)
        assertEquals(23.98, almostDay.hours, 0.001)
        assertFalse(almostDay.overnight)
    }

    @Test
    fun equalFullDay_eveningLunch_appliesOnTimeline() {
        // 22:00→22:00 (24h): wall 21:00–21:30 expands past midnight onto the
        // next evening inside the 24h window → lunch applies (23.50h).
        val applied = HoursCalc.worked(
            22 * 60,
            22 * 60,
            lunchOutMinutes = 21 * 60,
            lunchInMinutes = 21 * 60 + 30,
            equalOutMeansFullDay = true
        )
        assertEquals(23.50, applied.hours, 0.001)
        assertTrue(applied.lunchApplied)
        assertTrue(applied.overnight)
        // Equal lunch out/in still ignored on a full-day shift
        val ignored = HoursCalc.worked(
            22 * 60,
            22 * 60,
            lunchOutMinutes = 2 * 60,
            lunchInMinutes = 2 * 60,
            equalOutMeansFullDay = true
        )
        assertEquals(24.00, ignored.hours, 0.001)
        assertFalse(ignored.lunchApplied)
    }

    @Test
    fun unpaidBreakDuration_subtracted() {
        // 9:00–17:00 minus 30m unpaid = 7.50h
        val w = HoursCalc.worked(
            9 * 60, 17 * 60,
            breakDurationMinutes = 30,
            breakPaid = false
        )
        assertEquals(7.50, w.hours, 0.001)
        assertTrue(w.breakDurationApplied)
        assertFalse(w.lunchApplied)
    }

    @Test
    fun paidBreakDuration_notSubtracted() {
        val w = HoursCalc.worked(
            9 * 60, 17 * 60,
            breakDurationMinutes = 30,
            breakPaid = true
        )
        assertEquals(8.00, w.hours, 0.001)
        assertFalse(w.breakDurationApplied)
    }

    @Test
    fun lunchTimes_winOverBreakDuration() {
        // Timed lunch 30m wins over a 60m duration claim → 8.50h from 8–17
        val w = HoursCalc.worked(
            8 * 60, 17 * 60,
            lunchOutMinutes = 12 * 60,
            lunchInMinutes = 12 * 60 + 30,
            breakDurationMinutes = 60,
            breakPaid = false
        )
        assertEquals(8.50, w.hours, 0.001)
        assertTrue(w.lunchApplied)
        assertFalse(w.breakDurationApplied)
    }

    @Test
    fun breakDuration_cappedAtGross() {
        val w = HoursCalc.worked(
            9 * 60, 10 * 60,
            breakDurationMinutes = 120,
            breakPaid = false
        )
        assertEquals(0.00, w.hours, 0.001)
        assertTrue(w.breakDurationApplied)
    }

    @Test
    fun lunchWindowForBreakDuration_prefersNoon() {
        val window = HoursCalc.lunchWindowForBreakDuration(9 * 60, 17 * 60, 30)
        assertEquals(12 * 60 to 12 * 60 + 30, window)
    }

    @Test
    fun formatDayLabel_breakDuration() {
        val prev = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals(
                "9:00 AM – 5:00 PM  ·  Break 30m",
                HoursCalc.formatDayLabel(9 * 60, 17 * 60, null, null, 30)
            )
        } finally {
            Locale.setDefault(prev)
        }
    }
}
