package com.rmltd.workhourstracker.data.sync

import com.rmltd.workhourstracker.data.DailyEntry
import com.rmltd.workhourstracker.data.HoursSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncMergeTest {

    private fun entry(
        day: Long,
        hours: Double,
        updated: Long,
        source: String = HoursSource.CLOCK.name,
        noLunch: Boolean = false
    ) = DailyEntry(
        dateEpochDay = day,
        hoursWorked = hours,
        weekStartEpochDay = day,
        updatedAtEpochMillis = updated,
        clockInMinutes = 9 * 60,
        clockOutMinutes = 17 * 60,
        hoursSource = source,
        noLunchTaken = noLunch
    )

    @Test
    fun lww_prefersNewerUpdatedAt() {
        val local = entry(1, 8.0, updated = 100)
        val remote = entry(1, 7.5, updated = 200)
        val r = CloudSyncMerge.merge(listOf(local), listOf(remote))
        assertEquals(7.5, r.winners[1]!!.hoursWorked, 0.0)
        assertEquals(200, r.winners[1]!!.updatedAtEpochMillis)
    }

    @Test
    fun lww_keepsLocalWhenEqualOrNewer() {
        val local = entry(2, 8.0, updated = 300)
        val remote = entry(2, 1.0, updated = 100)
        val r = CloudSyncMerge.merge(listOf(local), listOf(remote))
        assertEquals(8.0, r.winners[2]!!.hoursWorked, 0.0)
    }

    @Test
    fun tombstone_winsWhenNewerThanEntry() {
        val local = entry(3, 8.0, updated = 100)
        val r = CloudSyncMerge.merge(
            listOf(local),
            emptyList(),
            localTombstones = emptyMap(),
            remoteTombstones = mapOf(3L to 200L)
        )
        assertTrue(r.deletions.contains(3L))
        assertFalse(r.winners.containsKey(3L))
    }

    @Test
    fun entry_winsWhenNewerThanTombstone() {
        val local = entry(4, 8.0, updated = 300)
        val r = CloudSyncMerge.merge(
            listOf(local),
            emptyList(),
            remoteTombstones = mapOf(4L to 100L)
        )
        assertTrue(r.winners.containsKey(4L))
        assertFalse(r.deletions.contains(4L))
    }

    @Test
    fun mergesTypedAndNoLunchFields() {
        val remote = entry(5, 0.0, updated = 50, source = HoursSource.TYPED.name, noLunch = true)
        val r = CloudSyncMerge.merge(emptyList(), listOf(remote))
        assertEquals(HoursSource.TYPED.name, r.winners[5]!!.hoursSource)
        assertTrue(r.winners[5]!!.noLunchTaken)
    }
}
