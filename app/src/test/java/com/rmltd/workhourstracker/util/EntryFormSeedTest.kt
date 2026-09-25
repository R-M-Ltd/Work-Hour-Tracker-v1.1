package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryFormSeedTest {

    @Test
    fun empty_hasBlankClocksAndComments() {
        val e = EntryFormSeed.empty()
        assertNull(e.clockInMinutes)
        assertNull(e.clockOutMinutes)
        assertNull(e.lunchOutMinutes)
        assertNull(e.lunchInMinutes)
        assertEquals("", e.comments)
        assertNull(e.breakDurationMinutes)
        assertFalse(e.breakPaid)
    }

    @Test
    fun fromLoaded_mapsClocksAndComments() {
        val f = EntryFormSeed.fromLoaded(
            clockInMinutes = 7 * 60 + 30,
            clockOutMinutes = 16 * 60,
            lunchOutMinutes = 12 * 60,
            lunchInMinutes = 12 * 60 + 30,
            comments = "warehouse"
        )
        assertEquals(7 * 60 + 30, f.clockInMinutes)
        assertEquals(16 * 60, f.clockOutMinutes)
        assertEquals(12 * 60, f.lunchOutMinutes)
        assertEquals(12 * 60 + 30, f.lunchInMinutes)
        assertEquals("warehouse", f.comments)
        assertNull(f.breakDurationMinutes)
        assertFalse(f.breakPaid)
    }

    @Test
    fun fromLoaded_mapsBreakDurationAndPaid() {
        val f = EntryFormSeed.fromLoaded(
            clockInMinutes = 9 * 60,
            clockOutMinutes = 17 * 60,
            lunchOutMinutes = null,
            lunchInMinutes = null,
            comments = null,
            breakDurationMinutes = 30,
            breakPaid = true
        )
        assertEquals(30, f.breakDurationMinutes)
        assertTrue(f.breakPaid)
    }

    @Test
    fun fromLoaded_nullCommentsBecomeEmpty() {
        val f = EntryFormSeed.fromLoaded(480, 1020, null, null, null)
        assertEquals("", f.comments)
        assertNull(f.lunchOutMinutes)
    }

    @Test
    fun preferDateScoped_usesMatchingRow() {
        val scoped = EntryFormSeed.fromLoaded(480, 1020, null, null, "scoped")
        val picked = EntryFormSeed.preferDateScoped(
            targetEpochDay = 10L,
            dateScopedEpochDay = 10L,
            dateScopedFields = scoped
        )
        assertEquals("scoped", picked.comments)
        assertEquals(480, picked.clockInMinutes)
    }

    @Test
    fun preferDateScoped_mismatchOrNull_yieldsEmpty_notInvented() {
        val scoped = EntryFormSeed.fromLoaded(480, 1020, null, null, "wrong-day")
        val mismatch = EntryFormSeed.preferDateScoped(10L, 9L, scoped)
        assertEquals(EntryFormSeed.empty(), mismatch)

        val missing = EntryFormSeed.preferDateScoped(10L, null, null)
        assertEquals(EntryFormSeed.empty(), missing)
    }

    @Test
    fun isOutsideConfiguredWeek_detectsOvernightAcrossBoundary() {
        // Configured week Wed–Tue: epochs 100..106. Yesterday=99 is outside.
        assertTrue(EntryFormSeed.isOutsideConfiguredWeek(99L, 100L, 106L))
        assertFalse(EntryFormSeed.isOutsideConfiguredWeek(100L, 100L, 106L))
        assertFalse(EntryFormSeed.isOutsideConfiguredWeek(106L, 100L, 106L))
        assertTrue(EntryFormSeed.isOutsideConfiguredWeek(107L, 100L, 106L))
    }
}
