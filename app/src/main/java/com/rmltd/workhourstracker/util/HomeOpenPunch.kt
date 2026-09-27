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
 */
object HomeOpenPunch {

    enum class Action {
        /** Empty / no row → start open punch at chosen minutes. */
        START_OPEN,
        /** Already open → update clock-in minutes on the open row. */
        UPDATE_OPEN,
        /** Closed or legacy → keep local only; full save still required. */
        LOCAL_ONLY
    }

    fun decide(
        todayIn: Int?,
        todayOut: Int?,
        todayHoursWorked: Double
    ): Action = when (ClockDayState.classify(todayIn, todayOut, todayHoursWorked)) {
        ClockDayState.Kind.EMPTY -> Action.START_OPEN
        ClockDayState.Kind.OPEN -> Action.UPDATE_OPEN
        ClockDayState.Kind.CLOSED, ClockDayState.Kind.LEGACY_CLOSED -> Action.LOCAL_ONLY
    }
}
