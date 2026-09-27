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

    @Test
    fun decide_overnightPending_empty_showsOvernight() {
        assertEquals(
            HomeOpenPunch.Action.SHOW_OVERNIGHT,
            HomeOpenPunch.decide(
                todayIn = null,
                todayOut = null,
                todayHoursWorked = 0.0,
                overnightOrOrphanPending = true
            )
        )
    }

    @Test
    fun decide_overnightPending_stillOpen_allowsUpdate() {
        // Today already open: picker may update today's in even if UI somehow flagged overnight.
        assertEquals(
            HomeOpenPunch.Action.UPDATE_OPEN,
            HomeOpenPunch.decide(
                todayIn = 9 * 60,
                todayOut = null,
                todayHoursWorked = 0.0,
                overnightOrOrphanPending = true
            )
        )
    }

    @Test
    fun decide_overnightPending_closed_localOnly() {
        assertEquals(
            HomeOpenPunch.Action.LOCAL_ONLY,
            HomeOpenPunch.decide(
                todayIn = 9 * 60,
                todayOut = 17 * 60,
                todayHoursWorked = 8.0,
                overnightOrOrphanPending = true
            )
        )
    }

    @Test
    fun punchCommentsForOpenIn_preservesDraftWhenNoMidnightReason() {
        val draft = "12:00 AM (Clock out): Left at midnight"
        assertEquals(
            draft,
            HomeOpenPunch.punchCommentsForOpenIn(draft, reasonNote = null)
        )
        assertEquals(
            draft,
            HomeOpenPunch.punchCommentsForOpenIn(draft, reasonNote = "  ")
        )
        assertEquals(
            null,
            HomeOpenPunch.punchCommentsForOpenIn("", reasonNote = null)
        )
    }

    @Test
    fun punchCommentsForOpenIn_mergesMidnightReasonOntoDraft() {
        val draft = "12:00 AM (Clock out): Left at midnight"
        assertEquals(
            "12:00 AM (Clock out): Left at midnight\n12:00 AM (Clock in): early",
            HomeOpenPunch.punchCommentsForOpenIn(draft, reasonNote = "early")
        )
        assertEquals(
            "12:00 AM (Clock in): early",
            HomeOpenPunch.punchCommentsForOpenIn("", reasonNote = "early")
        )
    }
}
