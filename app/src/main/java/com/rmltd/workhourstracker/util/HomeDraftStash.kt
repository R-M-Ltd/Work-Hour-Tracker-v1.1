package com.rmltd.workhourstracker.util

/**
 * Pure helpers for S-B durable Home draft (process-death survival).
 * Persistence I/O lives in [com.rmltd.workhourstracker.data.HomeDraftPreferences].
 *
 * 1.3.31 D1: [decideRestore] is Room-aware — Apply only when locals still match
 * Room and stash is dirty vs Room; clear silently when Room already won.
 * D2: comment equality uses [String.trim] like [isDirty].
 */
data class HomeDraftSnapshot(
    val epochDay: Long,
    val inMinutes: Int?,
    val outMinutes: Int?,
    val comments: String
)

sealed class HomeDraftRestoreDecision {
    /** No stash, or stash already matches local — do nothing. */
    data object None : HomeDraftRestoreDecision()

    /** Stash keyed to a prior day — clear and ignore. */
    data object ClearStaleDay : HomeDraftRestoreDecision()

    /**
     * Room already has an authoritative save that differs from stash
     * (Entry / other day-save won) — clear prefs; do not Apply.
     */
    data object ClearStaleDraft : HomeDraftRestoreDecision()

    /** Apply stash into Compose local state (genuine process-death restore). */
    data class Apply(val snapshot: HomeDraftSnapshot) : HomeDraftRestoreDecision()
}

object HomeDraftStash {

    /**
     * True when local Home draft differs from Room-backed values
     * (IN minutes, OUT minutes, or comments).
     */
    fun isDirty(
        localIn: Int?,
        localOut: Int?,
        localComments: String,
        roomIn: Int?,
        roomOut: Int?,
        roomComments: String
    ): Boolean =
        localIn != roomIn ||
            localOut != roomOut ||
            localComments.trim() != roomComments.trim()

    /**
     * Persist only when dirty and local draft is non-empty.
     * Avoids writing an empty wipe over a Room-backed day.
     */
    fun shouldPersist(
        localIn: Int?,
        localOut: Int?,
        localComments: String,
        roomIn: Int?,
        roomOut: Int?,
        roomComments: String
    ): Boolean {
        if (!isDirty(localIn, localOut, localComments, roomIn, roomOut, roomComments)) {
            return false
        }
        val localEmpty =
            localIn == null && localOut == null && localComments.isBlank()
        return !localEmpty
    }

    /**
     * Decide restore on Home enter for [todayEpochDay].
     *
     * Apply **only** when Compose locals still match Room (no newer authoritative
     * save) **and** stash is dirty versus Room. When Room already differs from
     * stash, return [HomeDraftRestoreDecision.ClearStaleDraft] (caller clears
     * prefs; no toast). Comment compares use [String.trim] (D2).
     */
    fun decideRestore(
        stash: HomeDraftSnapshot?,
        todayEpochDay: Long,
        localIn: Int?,
        localOut: Int?,
        localComments: String,
        roomIn: Int?,
        roomOut: Int?,
        roomComments: String
    ): HomeDraftRestoreDecision {
        if (stash == null) return HomeDraftRestoreDecision.None
        if (stash.epochDay != todayEpochDay) {
            return HomeDraftRestoreDecision.ClearStaleDay
        }
        val stashEmpty =
            stash.inMinutes == null &&
                stash.outMinutes == null &&
                stash.comments.isBlank()
        if (stashEmpty) return HomeDraftRestoreDecision.None

        val sameAsLocal =
            stash.inMinutes == localIn &&
                stash.outMinutes == localOut &&
                stash.comments.trim() == localComments.trim()
        if (sameAsLocal) return HomeDraftRestoreDecision.None

        val localEqualsRoom =
            localIn == roomIn &&
                localOut == roomOut &&
                localComments.trim() == roomComments.trim()
        val stashDirtyVsRoom =
            stash.inMinutes != roomIn ||
                stash.outMinutes != roomOut ||
                stash.comments.trim() != roomComments.trim()

        // Genuine process-death / leave-without-save: locals still Room-seeded,
        // stash holds unsaved edits.
        if (localEqualsRoom && stashDirtyVsRoom) {
            return HomeDraftRestoreDecision.Apply(stash)
        }

        // Room already differs from stash (Entry/other save won) — clear, no Apply.
        if (stashDirtyVsRoom) {
            return HomeDraftRestoreDecision.ClearStaleDraft
        }

        return HomeDraftRestoreDecision.None
    }
}
