package com.rmltd.workhourstracker.util

import com.rmltd.workhourstracker.data.DailyEntry

/**
 * Pure EOD gate: empty / incomplete / complete for today's log.
 * Overnight open on another day counts as incomplete (open Home banner).
 */
object EndOfDayCompleteness {

    enum class State {
        EMPTY,
        INCOMPLETE,
        COMPLETE
    }

    fun classify(
        todayEntry: DailyEntry?,
        overnightOrOrphanOpen: Boolean
    ): State {
        if (overnightOrOrphanOpen) return State.INCOMPLETE
        if (todayEntry == null) return State.EMPTY
        val open = todayEntry.clockInMinutes != null && todayEntry.clockOutMinutes == null
        if (open) return State.INCOMPLETE
        if (todayEntry.hasPersistedHours()) return State.COMPLETE
        return State.EMPTY
    }

    data class Copy(val title: String, val body: String)

    fun notificationCopy(state: State): Copy? = when (state) {
        State.EMPTY -> Copy(
            title = "Wrap up today?",
            body = "No hours logged yet. Add time or clock out if you worked."
        )
        State.INCOMPLETE -> Copy(
            title = "Still clocked in",
            body = "Today looks unfinished. Open the app to wrap up."
        )
        State.COMPLETE -> null
    }
}
