package com.rmltd.workhourstracker.widget

import com.rmltd.workhourstracker.data.ClockDayState
import com.rmltd.workhourstracker.util.HoursCalc

/**
 * Pure Home-screen widget copy from day punches + week totals.
 * No Context / RemoteViews — unit-testable.
 *
 * Clock times and hours both use Locale.US via [HoursCalc] (stable vs device locale).
 */
object WidgetContent {

    /** Overnight-open status when today is still empty (tap opens app; no widget resolve). */
    const val OVERNIGHT_OPEN_STATUS = "Overnight open — open app to finish"

    data class Display(
        /** Today's clock status line. */
        val statusLine: String,
        /** Week hours so far vs goal. */
        val weekLine: String
    )

    /**
     * @param overnightPending any other-day open punch (yesterday overnight or older orphan)
     * @param weekHours sum of current-week [DailyEntry.hoursWorked]
     * @param weekGoalHours Settings weekly goal
     */
    fun build(
        todayIn: Int?,
        todayOut: Int?,
        todayHoursWorked: Double,
        overnightPending: Boolean,
        weekHours: Double,
        weekGoalHours: Double
    ): Display {
        val kind = ClockDayState.classify(todayIn, todayOut, todayHoursWorked)
        val status = when {
            overnightPending && kind == ClockDayState.Kind.EMPTY ->
                OVERNIGHT_OPEN_STATUS
            kind == ClockDayState.Kind.OPEN && todayIn != null ->
                "Clocked in since ${HoursCalc.formatClock(todayIn)}"
            kind == ClockDayState.Kind.CLOSED -> {
                val range = HoursCalc.formatRange(todayIn, todayOut)
                if (range != null) {
                    "Completed · $range · ${HoursCalc.formatHours(todayHoursWorked)}"
                } else {
                    "Completed · ${HoursCalc.formatHours(todayHoursWorked)}"
                }
            }
            kind == ClockDayState.Kind.LEGACY_CLOSED ->
                "Completed · ${HoursCalc.formatHours(todayHoursWorked)}"
            else -> "Not clocked in"
        }
        val week =
            "Week ${HoursCalc.formatHours(weekHours)} / ${HoursCalc.formatHours(weekGoalHours)}"
        return Display(statusLine = status, weekLine = week)
    }
}
