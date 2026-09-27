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
            HomeDraftStash.decideRestore(null, today, null, null, "")
        )
    }

    @Test
    fun decideRestore_staleDay_clears() {
        val stash = HomeDraftSnapshot(today - 1, 480, 0, "old")
        assertEquals(
            HomeDraftRestoreDecision.ClearStaleDay,
            HomeDraftStash.decideRestore(stash, today, null, null, "")
        )
    }

    @Test
    fun decideRestore_sameAsLocal_none() {
        val stash = HomeDraftSnapshot(today, 480, 0, "mid")
        assertEquals(
            HomeDraftRestoreDecision.None,
            HomeDraftStash.decideRestore(stash, today, 480, 0, "mid")
        )
    }

    @Test
    fun decideRestore_emptyStash_none() {
        val stash = HomeDraftSnapshot(today, null, null, "")
        assertEquals(
            HomeDraftRestoreDecision.None,
            HomeDraftStash.decideRestore(stash, today, null, null, "")
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
            localComments = ""
        )
        assertEquals(HomeDraftRestoreDecision.Apply(stash), decision)
    }

    @Test
    fun decideRestore_appliesFullDraftWhenLocalEmpty() {
        val stash = HomeDraftSnapshot(today, 540, 1020, "note")
        val decision = HomeDraftStash.decideRestore(
            stash, today, null, null, ""
        )
        assertEquals(HomeDraftRestoreDecision.Apply(stash), decision)
    }
}
