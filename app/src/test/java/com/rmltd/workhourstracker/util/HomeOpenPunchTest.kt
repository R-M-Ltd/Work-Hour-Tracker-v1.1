package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeOpenPunchTest {

    @Test
    fun decide_empty_startsOpen() {
        assertEquals(
            HomeOpenPunch.Action.START_OPEN,
            HomeOpenPunch.decide(null, null, 0.0)
        )
    }

    @Test
    fun decide_open_updatesOpen() {
        assertEquals(
            HomeOpenPunch.Action.UPDATE_OPEN,
            HomeOpenPunch.decide(9 * 60, null, 0.0)
        )
    }

    @Test
    fun decide_closedOrLegacy_localOnly() {
        assertEquals(
            HomeOpenPunch.Action.LOCAL_ONLY,
            HomeOpenPunch.decide(9 * 60, 17 * 60, 8.0)
        )
        assertEquals(
            HomeOpenPunch.Action.LOCAL_ONLY,
            HomeOpenPunch.decide(null, null, 8.0)
        )
    }
}
