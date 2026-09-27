package com.rmltd.workhourstracker.util

/**
 * Pure helpers for S-B durable Home draft (process-death survival).
 * Persistence I/O lives in [com.rmltd.workhourstracker.data.HomeDraftPreferences].
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

    /** Apply stash into Compose local state (process-death restore). */
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
     * Clear when stash epoch ≠ today; Apply when stash differs from local
     * (Room-initialized) state and is non-empty.
     */
    fun decideRestore(
        stash: HomeDraftSnapshot?,
        todayEpochDay: Long,
        localIn: Int?,
        localOut: Int?,
        localComments: String
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
        val same =
            stash.inMinutes == localIn &&
                stash.outMinutes == localOut &&
                stash.comments == localComments
        if (same) return HomeDraftRestoreDecision.None
        return HomeDraftRestoreDecision.Apply(stash)
    }
}
