package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDraftStashTest {

    private val today = 20_000L

    @Test
    fun isDirty_detectsInOutComments() {
        assertFalse(
            HomeDraftStash.isDirty(480, 1020, "note", 480, 1020, "note")
        )
        assertTrue(
            HomeDraftStash.isDirty(480, 1020, "note", 480, null, "note")
        )
        assertTrue(
            HomeDraftStash.isDirty(480, 0, "12:00 AM (Clock out): mid", 480, null, "")
        )
        assertTrue(
            HomeDraftStash.isDirty(480, 1020, "a", 480, 1020, "b")
        )
        assertFalse(
            HomeDraftStash.isDirty(480, 1020, "  note  ", 480, 1020, "note")
        )
    }

    @Test
    fun shouldPersist_requiresDirtyNonEmpty() {
        assertTrue(
            HomeDraftStash.shouldPersist(
                480, 0, "12:00 AM (Clock out): mid",
                480, null, ""
            )
        )
        assertFalse(
            HomeDraftStash.shouldPersist(480, 1020, "note", 480, 1020, "note")
        )
        // Empty local vs Room populated — do not stash a wipe
        assertFalse(
            HomeDraftStash.shouldPersist(null, null, "", 480, 1020, "saved")
        )
        // Comments-only dirty still persists
        assertTrue(
            HomeDraftStash.shouldPersist(480, null, "draft", 480, null, "")
        )
    }

    @Test
    fun decideRestore_nullStash_none() {
        assertEquals(
            HomeDraftRestoreDecision.None,
            HomeDraftStash.decideRestore(
                null, today, null, null, "",
                null, null, ""
            )
        )
    }

    @Test
    fun decideRestore_staleDay_clears() {
        val stash = HomeDraftSnapshot(today - 1, 480, 0, "old")
        assertEquals(
            HomeDraftRestoreDecision.ClearStaleDay,
            HomeDraftStash.decideRestore(
                stash, today, null, null, "",
                null, null, ""
            )
        )
    }

    @Test
    fun decideRestore_sameAsLocal_none() {
        val stash = HomeDraftSnapshot(today, 480, 0, "mid")
        assertEquals(
            HomeDraftRestoreDecision.None,
            HomeDraftStash.decideRestore(
                stash, today, 480, 0, "mid",
                480, null, ""
            )
        )
    }

    @Test
    fun decideRestore_emptyStash_none() {
        val stash = HomeDraftSnapshot(today, null, null, "")
        assertEquals(
            HomeDraftRestoreDecision.None,
            HomeDraftStash.decideRestore(
                stash, today, null, null, "",
                null, null, ""
            )
        )
    }

    @Test
    fun decideRestore_processDeath_appliesOutAndComments() {
        // Room/local after death: IN from Room, OUT/comments gone
        val stash = HomeDraftSnapshot(
            epochDay = today,
            inMinutes = 480,
            outMinutes = 0,
            comments = "12:00 AM (Clock out): Left at midnight"
        )
        val decision = HomeDraftStash.decideRestore(
            stash = stash,
            todayEpochDay = today,
            localIn = 480,
            localOut = null,
            localComments = "",
            roomIn = 480,
            roomOut = null,
            roomComments = ""
        )
        assertEquals(HomeDraftRestoreDecision.Apply(stash), decision)
    }

    @Test
    fun decideRestore_appliesFullDraftWhenLocalEmpty() {
        val stash = HomeDraftSnapshot(today, 540, 1020, "note")
        // Locals still Room-seeded (empty day); stash dirty vs Room
        val decision = HomeDraftStash.decideRestore(
            stash, today, null, null, "",
            null, null, ""
        )
        assertEquals(HomeDraftRestoreDecision.Apply(stash), decision)
    }

    /**
     * D1: Entry save updates Room then D1-a clears stash — Home sees null stash.
     * (If stash were still present with local==Room, decideRestore would Apply;
     * clearing on day-save is what prevents the clobber.)
     */
    @Test
    fun decideRestore_afterEntrySaveClearedStash_none() {
        assertEquals(
            HomeDraftRestoreDecision.None,
            HomeDraftStash.decideRestore(
                stash = null,
                todayEpochDay = today,
                localIn = 480,
                localOut = 1020,
                localComments = "entry note",
                roomIn = 480,
                roomOut = 1020,
                roomComments = "entry note"
            )
        )
    }

    /** D1: local already diverged from Room (user editing) while Room ≠ stash → clear. */
    @Test
    fun decideRestore_roomDiffersFromStash_localDirty_clears() {
        val stash = HomeDraftSnapshot(today, 480, 0, "old")
        val decision = HomeDraftStash.decideRestore(
            stash = stash,
            todayEpochDay = today,
            localIn = 480,
            localOut = 900,
            localComments = "editing",
            roomIn = 480,
            roomOut = 1020,
            roomComments = "saved"
        )
        assertEquals(HomeDraftRestoreDecision.ClearStaleDraft, decision)
    }

    /** D2: whitespace-only comment drift must not Apply when trimmed equal. */
    @Test
    fun decideRestore_whitespaceOnlyComment_none() {
        val stash = HomeDraftSnapshot(today, 480, 1020, "note ")
        val decision = HomeDraftStash.decideRestore(
            stash = stash,
            todayEpochDay = today,
            localIn = 480,
            localOut = 1020,
            localComments = "note",
            roomIn = 480,
            roomOut = 1020,
            roomComments = "note"
        )
        assertEquals(HomeDraftRestoreDecision.None, decision)
    }

    /** D2: trim also gates Apply when stash/local comments differ only by spaces. */
    @Test
    fun decideRestore_stashVsLocalTrimEqual_noneEvenIfRoomDiffers() {
        // stash == local after trim → None (user already has draft applied / editing)
        val stash = HomeDraftSnapshot(today, 480, 0, "  mid  ")
        val decision = HomeDraftStash.decideRestore(
            stash = stash,
            todayEpochDay = today,
            localIn = 480,
            localOut = 0,
            localComments = "mid",
            roomIn = 480,
            roomOut = null,
            roomComments = ""
        )
        assertEquals(HomeDraftRestoreDecision.None, decision)
    }

    @Test
    fun decideCommentsOnlyReconcile_dirtyOut_keepsClocksUpdatesComments() {
        val stash = HomeDraftSnapshot(
            epochDay = 10L,
            inMinutes = 480,
            outMinutes = 1020,
            comments = "draft note"
        )
        val decision = HomeDraftStash.decideCommentsOnlyReconcile(
            stash = stash,
            epochDay = 10L,
            newComments = "history note",
            roomIn = 480,
            roomOut = null // dirty OUT
        )
        val keep = decision as CommentsOnlyReconcileDecision.KeepClocksUpdateComments
        assertEquals(480, keep.snapshot.inMinutes)
        assertEquals(1020, keep.snapshot.outMinutes)
        assertEquals("history note", keep.snapshot.comments)
        assertEquals(10L, keep.snapshot.epochDay)
    }

    @Test
    fun decideCommentsOnlyReconcile_clocksEqualRoom_clears() {
        val stash = HomeDraftSnapshot(
            epochDay = 10L,
            inMinutes = 480,
            outMinutes = 1020,
            comments = "only comments dirty"
        )
        val decision = HomeDraftStash.decideCommentsOnlyReconcile(
            stash = stash,
            epochDay = 10L,
            newComments = "saved note",
            roomIn = 480,
            roomOut = 1020
        )
        assertEquals(CommentsOnlyReconcileDecision.Clear, decision)
    }

    @Test
    fun decideCommentsOnlyReconcile_nullOrWrongEpoch_noOp() {
        assertEquals(
            CommentsOnlyReconcileDecision.NoOp,
            HomeDraftStash.decideCommentsOnlyReconcile(null, 10L, "n", 480, 1020)
        )
        val stash = HomeDraftSnapshot(9L, 480, 1020, "x")
        assertEquals(
            CommentsOnlyReconcileDecision.NoOp,
            HomeDraftStash.decideCommentsOnlyReconcile(stash, 10L, "n", 480, null)
        )
    }

    @Test
    fun decideCommentsOnlyReconcile_emptyClocksMatchingNullRoom_clears() {
        val stash = HomeDraftSnapshot(10L, null, null, "comments only")
        assertEquals(
            CommentsOnlyReconcileDecision.Clear,
            HomeDraftStash.decideCommentsOnlyReconcile(stash, 10L, "new", null, null)
        )
    }
}
