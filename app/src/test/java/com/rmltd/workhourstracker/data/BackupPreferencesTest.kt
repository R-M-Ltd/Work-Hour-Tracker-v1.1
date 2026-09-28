package com.rmltd.workhourstracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPreferencesTest {

    @Test
    fun staleThreshold_isSevenDays() {
        assertEquals(7L * 24 * 60 * 60 * 1000, BackupPreferences.STALE_AFTER_MILLIS)
    }

    @Test
    fun never_isStale() {
        assertTrue(BackupPreferences.isStale(0L, System.currentTimeMillis()))
        assertTrue(BackupPreferences.isStale(-1L, 1_000L))
    }

    @Test
    fun fresh_notStale() {
        val now = 1_000_000_000_000L
        assertFalse(BackupPreferences.isStale(now - 3L * 24 * 60 * 60 * 1000, now))
    }

    @Test
    fun olderThanSevenDays_stale() {
        val now = 1_000_000_000_000L
        assertTrue(
            BackupPreferences.isStale(
                now - BackupPreferences.STALE_AFTER_MILLIS - 1,
                now
            )
        )
    }
}
