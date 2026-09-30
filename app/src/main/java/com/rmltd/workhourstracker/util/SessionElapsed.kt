package com.rmltd.workhourstracker.util

import com.rmltd.workhourstracker.data.ClockDayState
import com.rmltd.workhourstracker.data.DailyEntry
import java.time.LocalTime
import kotlin.math.abs

/**
 * Shared today-hours helper for Home open-punch preview, widget Today line,
 * and goals ring actual. Open punches use [HoursCalc] with the entry's
 * break / no-lunch fields; closed days use persisted [DailyEntry.hoursWorked].
 *
 * Call sites must agree within [TOLERANCE_HOURS] (0.01h).
 *
 * When [pauseFreezeMinutes] is set (session paused), open-punch elapsed uses
 * that frozen minutes-from-midnight instead of live [nowMinutes] — pause does
 * not close the OPEN punch.
 */
object SessionElapsed {

    const val TOLERANCE_HOURS = 0.01

    /**
     * Today's display hours, or null when the user is not clocked in for today
     * (widget hides the Today line). Overnight-open on another day does not
     * invent today's total.
     *
     * @param nowMinutes minutes-from-midnight used for open-punch live elapsed
     * @param pauseFreezeMinutes when non-null, open punch uses this instead of [nowMinutes]
     */
    fun todayDisplayHours(
        todayEntry: DailyEntry?,
        nowMinutes: Int = LocalTime.now().hour * 60 + LocalTime.now().minute,
        pauseFreezeMinutes: Int? = null
    ): Double? {
        if (todayEntry == null) return null
        val kind = ClockDayState.classify(
            todayEntry.clockInMinutes,
            todayEntry.clockOutMinutes,
            todayEntry.hoursWorked
        )
        return when (kind) {
            ClockDayState.Kind.OPEN -> {
                val inM = todayEntry.clockInMinutes ?: return null
                val endM = (pauseFreezeMinutes ?: nowMinutes)
                    .coerceIn(0, HoursCalc.MINUTES_PER_DAY - 1)
                HoursCalc.hoursWorked(
                    clockInMinutes = inM,
                    clockOutMinutes = endM,
                    lunchOutMinutes = todayEntry.lunchOutMinutes,
                    lunchInMinutes = todayEntry.lunchInMinutes,
                    equalOutMeansFullDay = false,
                    breakDurationMinutes = todayEntry.breakDurationMinutes,
                    breakPaid = todayEntry.breakPaid,
                    noLunchTaken = todayEntry.noLunchTaken
                )
            }
            ClockDayState.Kind.CLOSED, ClockDayState.Kind.LEGACY_CLOSED ->
                todayEntry.hoursWorked
            ClockDayState.Kind.EMPTY -> null
        }
    }

    /**
     * Week actual for goals: sum of persisted [DailyEntry.hoursWorked] for
     * closed/typed days, plus live open-punch elapsed for today when open.
     * Open punches store 0 hoursWorked until close — replace today's 0 with live.
     */
    fun weekActualHours(
        weekEntries: List<DailyEntry>,
        todayEpochDay: Long,
        nowMinutes: Int = LocalTime.now().hour * 60 + LocalTime.now().minute,
        pauseFreezeMinutes: Int? = null
    ): Double {
        var sum = 0.0
        var todayHandled = false
        for (e in weekEntries) {
            if (e.dateEpochDay == todayEpochDay) {
                val live = todayDisplayHours(e, nowMinutes, pauseFreezeMinutes)
                if (live != null) {
                    sum += live
                    todayHandled = true
                    continue
                }
            }
            sum += e.hoursWorked
        }
        if (!todayHandled) {
            // today not in list but may still be open — caller usually includes it
        }
        return HoursCalcRound(sum)
    }

    /** True when [a] and [b] differ by at most [TOLERANCE_HOURS]. */
    fun withinTolerance(a: Double, b: Double): Boolean =
        abs(a - b) <= TOLERANCE_HOURS + 1e-9

    private fun HoursCalcRound(hours: Double): Double =
        kotlin.math.round(hours * 100.0) / 100.0
}
