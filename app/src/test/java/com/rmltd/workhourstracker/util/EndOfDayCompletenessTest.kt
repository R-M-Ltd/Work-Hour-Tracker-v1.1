package com.rmltd.workhourstracker.util

import com.rmltd.workhourstracker.data.DailyEntry
import com.rmltd.workhourstracker.data.HoursSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndOfDayCompletenessTest {

    private fun entry(
        inM: Int? = null,
        outM: Int? = null,
        hours: Double = 0.0,
        source: HoursSource = HoursSource.CLOCK
    ) = DailyEntry(
        dateEpochDay = 1L,
        hoursWorked = hours,
        weekStartEpochDay = 1L,
        clockInMinutes = inM,
        clockOutMinutes = outM,
        hoursSource = source.name
    )

    @Test
    fun empty_whenNoEntry() {
        assertEquals(
            EndOfDayCompleteness.State.EMPTY,
            EndOfDayCompleteness.classify(null, false)
        )
        val copy = EndOfDayCompleteness.notificationCopy(EndOfDayCompleteness.State.EMPTY)!!
        assertEquals("Wrap up today?", copy.title)
    }

    @Test
    fun incomplete_whenOpenOrOvernight() {
        assertEquals(
            EndOfDayCompleteness.State.INCOMPLETE,
            EndOfDayCompleteness.classify(entry(inM = 9 * 60), false)
        )
        assertEquals(
            EndOfDayCompleteness.State.INCOMPLETE,
            EndOfDayCompleteness.classify(null, true)
        )
        val copy = EndOfDayCompleteness.notificationCopy(EndOfDayCompleteness.State.INCOMPLETE)!!
        assertEquals("Still clocked in", copy.title)
    }

    @Test
    fun complete_skipsNotification() {
        assertEquals(
            EndOfDayCompleteness.State.COMPLETE,
            EndOfDayCompleteness.classify(entry(inM = 9 * 60, outM = 17 * 60, hours = 8.0), false)
        )
        assertEquals(
            EndOfDayCompleteness.State.COMPLETE,
            EndOfDayCompleteness.classify(
                entry(hours = 0.0, source = HoursSource.TYPED),
                false
            )
        )
        assertNull(EndOfDayCompleteness.notificationCopy(EndOfDayCompleteness.State.COMPLETE))
    }

    @Test
    fun copyHasNoClockOutActionRequirement() {
        // Documented: EOD actions are Open app + Dismiss only
        val incomplete = EndOfDayCompleteness.notificationCopy(
            EndOfDayCompleteness.State.INCOMPLETE
        )!!
        assertTrue(!incomplete.body.contains("Clock out", ignoreCase = true) ||
            incomplete.body.contains("Open the app"))
    }
}
