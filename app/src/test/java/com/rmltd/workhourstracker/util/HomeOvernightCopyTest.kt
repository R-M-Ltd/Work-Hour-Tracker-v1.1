package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HomeOvernightCopyTest {

    private val today = LocalDate.of(2026, 9, 25)
    private val yesterday = today.minusDays(1)
    private val orphan = today.minusDays(3)

    @Test
    fun clockOutOvernightToast_yesterday() {
        assertEquals(
            "Finished yesterday's overnight shift",
            HomeOvernightCopy.clockOutOvernightToast(yesterday, today)
        )
    }

    @Test
    fun clockOutOvernightToast_nullOpen_assumesYesterday() {
        assertEquals(
            "Finished yesterday's overnight shift",
            HomeOvernightCopy.clockOutOvernightToast(null, today)
        )
    }

    @Test
    fun clockOutOvernightToast_olderOrphan_neutral() {
        assertEquals(
            "Finished open overnight shift",
            HomeOvernightCopy.clockOutOvernightToast(orphan, today)
        )
    }

    @Test
    fun clockOutHelper_notPending() {
        assertEquals(
            "Sets time to now. Break/lunch: edit the day.",
            HomeOvernightCopy.clockOutHelper(false, orphan, today)
        )
    }

    @Test
    fun clockOutHelper_yesterday() {
        assertEquals(
            "Sets time to now (or finishes yesterday's overnight). Break/lunch: edit the day.",
            HomeOvernightCopy.clockOutHelper(true, yesterday, today)
        )
    }

    @Test
    fun clockOutHelper_orphan_neutral() {
        assertEquals(
            "Sets time to now (or finishes the open overnight). Break/lunch: edit the day.",
            HomeOvernightCopy.clockOutHelper(true, orphan, today)
        )
    }
}
