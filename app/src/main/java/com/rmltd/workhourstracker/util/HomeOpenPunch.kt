package com.rmltd.workhourstracker.util

import com.rmltd.workhourstracker.data.ClockDayState

/**
 * Pure decisions for Home "Clock in" TimePicker when only an open punch (in, no out)
 * should be written — same Room shape as
 * [com.rmltd.workhourstracker.data.WorkHoursRepository.clockInNow].
 *
 * Previously the Home TimePicker only mutated Compose state until "Save today's times"
 * (which requires both in and out). That made a filled Clock-in time look persisted
 * when it was not. Open-punch confirms must go through the clock-in write path.
 *
 * When an overnight/orphan open exists and today is still empty, gate like
 * Clock-in-now ([Action.SHOW_OVERNIGHT]) — do not start a new open punch.
 */
object HomeOpenPunch {

    enum class Action {
        /** Empty / no row → start open punch at chosen minutes. */
        START_OPEN,
        /** Already open → update clock-in minutes on the open row. */
        UPDATE_OPEN,
        /** Closed or legacy → keep local only; full save still required. */
        LOCAL_ONLY,
        /** Overnight/orphan pending + today empty → show resolve UI (no write). */
        SHOW_OVERNIGHT
    }

    fun decide(
        todayIn: Int?,
        todayOut: Int?,
        todayHoursWorked: Double,
        overnightOrOrphanPending: Boolean = false
    ): Action {
        val kind = ClockDayState.classify(todayIn, todayOut, todayHoursWorked)
        if (overnightOrOrphanPending && kind == ClockDayState.Kind.EMPTY) {
            return Action.SHOW_OVERNIGHT
        }
        return when (kind) {
            ClockDayState.Kind.EMPTY -> Action.START_OPEN
            ClockDayState.Kind.OPEN -> Action.UPDATE_OPEN
            ClockDayState.Kind.CLOSED, ClockDayState.Kind.LEGACY_CLOSED -> Action.LOCAL_ONLY
        }
    }
}
