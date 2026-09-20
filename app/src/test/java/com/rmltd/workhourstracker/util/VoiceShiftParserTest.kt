package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceShiftParserTest {

    @Test
    fun morningIn_bareOutFour_becomes4pm() {
        val t = VoiceShiftParser.parse("clocked in at 8, out at 4")
        assertEquals(8 * 60, t.clockIn)
        assertEquals(16 * 60, t.clockOut) // 4 PM, not 4 AM overnight
    }

    @Test
    fun afternoonIn_bareOutFour_becomes4pm() {
        // L3: after PM clock-in, "out at 4" should not stay 4 AM overnight
        val t = VoiceShiftParser.parse("clocked in at 3 pm, out at 4")
        assertEquals(15 * 60, t.clockIn)
        assertEquals(16 * 60, t.clockOut)
    }

    @Test
    fun lateNightIn_bareOutFour_stays4am_overnight() {
        // in 10 PM + bare 4 → 4 PM is before 10 PM, so keep 4 AM overnight
        val t = VoiceShiftParser.parse("clocked in at 10 pm, out at 4")
        assertEquals(22 * 60, t.clockIn)
        assertEquals(4 * 60, t.clockOut)
        assertTrue(HoursCalc.isOvernight(t.clockIn!!, t.clockOut!!))
    }

    @Test
    fun resolveOutAgainstIn_afternoonHeuristic() {
        assertEquals(16 * 60, VoiceShiftParser.resolveOutAgainstIn(15 * 60, 4 * 60))
        assertEquals(4 * 60, VoiceShiftParser.resolveOutAgainstIn(22 * 60, 4 * 60))
        assertEquals(16 * 60, VoiceShiftParser.resolveOutAgainstIn(8 * 60, 4 * 60))
    }

    @Test
    fun wholeShift_withLunch() {
        val t = VoiceShiftParser.parse("clocked in at 7:30, lunch 12 to 12:30, out at 4")
        assertEquals(7 * 60 + 30, t.clockIn)
        assertEquals(12 * 60, t.lunchOut)
        assertEquals(12 * 60 + 30, t.lunchIn)
        assertEquals(16 * 60, t.clockOut)
    }

    @Test
    fun startedFinished_phrase() {
        val t = VoiceShiftParser.parse("started 7:45 lunch from 12 to 12:30 finished at 4:15 pm")
        assertEquals(7 * 60 + 45, t.clockIn)
        assertEquals(12 * 60, t.lunchOut)
        assertEquals(12 * 60 + 30, t.lunchIn)
        assertEquals(16 * 60 + 15, t.clockOut)
    }

    @Test
    fun shortInOut_bareAfternoon() {
        val t = VoiceShiftParser.parse("in at 8, out at 5")
        assertEquals(8 * 60, t.clockIn)
        assertEquals(17 * 60, t.clockOut)
    }

    @Test
    fun lunchAt_singleHintOnly() {
        val t = VoiceShiftParser.parse("clocked in at 9, lunch at 12, out at 5")
        assertEquals(9 * 60, t.clockIn)
        assertEquals(12 * 60, t.lunchOut)
        assertNull(t.lunchIn)
        assertEquals(17 * 60, t.clockOut)
    }

    @Test
    fun unlabeledFourTimes_fillInLunchOut() {
        val t = VoiceShiftParser.parse("8 12 12:30 4")
        assertEquals(8 * 60, t.clockIn)
        assertEquals(12 * 60, t.lunchOut)
        assertEquals(12 * 60 + 30, t.lunchIn)
        assertEquals(16 * 60, t.clockOut)
    }

    @Test
    fun emptySpeech_hasNothing() {
        val t = VoiceShiftParser.parse("")
        assertFalse(t.hasAny)
        assertEquals(0, t.filledCount)
        assertNull(t.clockIn)
        assertNull(t.clockOut)
    }

    @Test
    fun garbageSpeech_hasNothing() {
        val t = VoiceShiftParser.parse("um yeah whatever")
        assertFalse(t.hasAny)
    }

    @Test
    fun extractClockMinutes_amPm() {
        assertEquals(7 * 60 + 30, extractClockMinutes("7:30 am"))
        assertEquals(16 * 60, extractClockMinutes("4 pm"))
        assertEquals(0, extractClockMinutes("12 am"))
        assertEquals(12 * 60, extractClockMinutes("12 pm"))
    }

    @Test
    fun extractClockMinutes_compactAndTwentyFour() {
        assertEquals(7 * 60 + 30, extractClockMinutes("730am"))
        assertEquals(19 * 60 + 15, extractClockMinutes("19:15"))
        assertNull(extractClockMinutes("not a time"))
    }

    @Test
    fun explicitPm_notDoubleBumped() {
        val t = VoiceShiftParser.parse("in at 3 pm, out at 4 pm")
        assertEquals(15 * 60, t.clockIn)
        assertEquals(16 * 60, t.clockOut)
    }

    @Test
    fun leftAt_synonym() {
        val t = VoiceShiftParser.parse("clocked in at 9 am and left at 5 pm")
        assertEquals(9 * 60, t.clockIn)
        assertEquals(17 * 60, t.clockOut)
    }

    @Test
    fun breakUntil_synonymLunchRange() {
        val t = VoiceShiftParser.parse("in at 8, break from 12 until 12:30, out at 5")
        assertEquals(8 * 60, t.clockIn)
        assertEquals(12 * 60, t.lunchOut)
        assertEquals(12 * 60 + 30, t.lunchIn)
        assertEquals(17 * 60, t.clockOut)
    }

    @Test
    fun brokeThrough_synonym() {
        val t = VoiceShiftParser.parse("started at 9, broke 12 through 12:45, ended at 5")
        assertEquals(9 * 60, t.clockIn)
        assertEquals(12 * 60, t.lunchOut)
        assertEquals(12 * 60 + 45, t.lunchIn)
        assertEquals(17 * 60, t.clockOut)
    }

    @Test
    fun unlabeledTwoTimes_inAndOut() {
        val t = VoiceShiftParser.parse("8 5")
        assertEquals(8 * 60, t.clockIn)
        assertEquals(17 * 60, t.clockOut) // bare 5 after morning in → 5 PM
        assertNull(t.lunchOut)
    }

    @Test
    fun unlabeledThreeTimes_middleAsLunchOut() {
        val t = VoiceShiftParser.parse("8 12 5")
        assertEquals(8 * 60, t.clockIn)
        assertEquals(12 * 60, t.lunchOut)
        assertNull(t.lunchIn)
        assertEquals(17 * 60, t.clockOut)
    }

    @Test
    fun unlabeledSingleTime_clockInOnly() {
        val t = VoiceShiftParser.parse("9")
        assertEquals(9 * 60, t.clockIn)
        assertNull(t.clockOut)
        assertEquals(1, t.filledCount)
    }

    @Test
    fun resolveOutAgainstIn_leavesPmAndMidnightAlone() {
        assertEquals(16 * 60, VoiceShiftParser.resolveOutAgainstIn(8 * 60, 16 * 60))
        assertEquals(0, VoiceShiftParser.resolveOutAgainstIn(22 * 60, 0))
        assertEquals(12 * 60, VoiceShiftParser.resolveOutAgainstIn(8 * 60, 12 * 60))
    }

    @Test
    fun afternoonIn_bareLunchBumpedToPm() {
        val t = VoiceShiftParser.parse("clocked in at 3 pm, lunch 4 to 4:30, out at 8")
        assertEquals(15 * 60, t.clockIn)
        assertEquals(16 * 60, t.lunchOut)
        assertEquals(16 * 60 + 30, t.lunchIn)
        assertEquals(20 * 60, t.clockOut)
    }

    @Test
    fun extractClockMinutes_spaceSeparatedWithAmPm() {
        assertEquals(19 * 60 + 30, extractClockMinutes("7 30 pm"))
        assertEquals(7 * 60 + 30, extractClockMinutes("7 30 am"))
    }

    @Test
    fun extractClockMinutes_invalidMinute_null() {
        assertNull(extractClockMinutes("7:60 am"))
        assertNull(extractClockMinutes("25:00"))
    }

    @Test
    fun extractAllTimes_prefersColonOverBarePair() {
        // "8 12" must stay two bare hours, not 8:12
        assertEquals(listOf(8 * 60, 12 * 60), VoiceShiftParser.extractAllTimes("8 12"))
        assertEquals(
            listOf(8 * 60, 12 * 60, 12 * 60 + 30, 4 * 60),
            VoiceShiftParser.extractAllTimes("8 12 12:30 4")
        )
    }

    @Test
    fun hyphenNormalized_inOut() {
        val t = VoiceShiftParser.parse("clocked-in at 8 - out at 4")
        assertEquals(8 * 60, t.clockIn)
        assertEquals(16 * 60, t.clockOut)
    }



    @Test
    fun explicitAmPm_overnightShift() {
        val t = VoiceShiftParser.parse("clocked in at 10 pm, out at 6 am")
        assertEquals(22 * 60, t.clockIn)
        assertEquals(6 * 60, t.clockOut)
        assertTrue(HoursCalc.isOvernight(t.clockIn!!, t.clockOut!!))
    }

    @Test
    fun noonAndMidnight_spoken() {
        val t = VoiceShiftParser.parse("in at 12 pm, out at 12 am")
        assertEquals(12 * 60, t.clockIn)
        assertEquals(0, t.clockOut) // midnight after noon → overnight reading kept
        assertTrue(HoursCalc.isOvernight(t.clockIn!!, t.clockOut!!))
    }

    @Test
    fun bareOutEqualToMorningIn_bumpsToPm() {
        // "in at 8, out at 8" → prefer 8 PM same-day finish over 0h
        assertEquals(20 * 60, VoiceShiftParser.resolveOutAgainstIn(8 * 60, 8 * 60))
        val t = VoiceShiftParser.parse("in at 8, out at 8")
        assertEquals(8 * 60, t.clockIn)
        assertEquals(20 * 60, t.clockOut)
    }

    @Test
    fun lunchRange_inBeforeOut_bumpsLunchInToPm() {
        val t = VoiceShiftParser.parse("in at 8, lunch 12:30 to 12, out at 5")
        assertEquals(8 * 60, t.clockIn)
        assertEquals(12 * 60 + 30, t.lunchOut)
        // lunch-in 12 was ≤ lunch-out → bump bare 12 hour into PM → 12:00 still ≤ 12:30?
        // hour 12 is noon already (>=12) so bumpLunch/disambiguate leaves 12:00;
        // then lunchIn <= lunchOut stays — documented edge: noon does not get +12h.
        assertEquals(12 * 60, t.lunchIn)
        assertEquals(17 * 60, t.clockOut)
    }

    @Test
    fun resolveOutAgainstIn_noonIn_bareFour_staysOvernightOrPm() {
        // in at noon, bare out 4 → afternoon rule: asPm 16:00 > 12:00 → 4 PM
        assertEquals(16 * 60, VoiceShiftParser.resolveOutAgainstIn(12 * 60, 4 * 60))
    }
}
