package com.rmltd.workhourstracker.widget

import com.rmltd.workhourstracker.data.ClockDayState
import com.rmltd.workhourstracker.data.DailyEntry
import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.util.SessionElapsed

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
        /**
         * Running/closed today total, or null to hide (not clocked in).
         * Format e.g. "Today: 3.50h".
         */
        val todayLine: String?,
        /** Week hours so far vs goal. */
        val weekLine: String,
        /** True when an open punch needs ~60s refresh. */
        val openSession: Boolean
    )

    /**
     * @param overnightPending any other-day open punch (yesterday overnight or older orphan)
     * @param weekHours sum of current-week hours (caller may include live elapsed)
     * @param weekGoalHours Settings weekly goal
     * @param nowMinutes minutes-from-midnight for open-punch live elapsed
     */
    fun build(
        todayIn: Int?,
        todayOut: Int?,
        todayHoursWorked: Double,
        overnightPending: Boolean,
        weekHours: Double,
        weekGoalHours: Double,
        todayEntry: DailyEntry? = null,
        nowMinutes: Int = java.time.LocalTime.now().hour * 60 + java.time.LocalTime.now().minute
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
        val entry = todayEntry ?: if (todayIn != null || todayOut != null || todayHoursWorked > 0.0) {
            // Minimal synthetic for tests that omit full entry
            DailyEntry(
                dateEpochDay = 0L,
                hoursWorked = todayHoursWorked,
                weekStartEpochDay = 0L,
                clockInMinutes = todayIn,
                clockOutMinutes = todayOut
            )
        } else null
        val todayHours = SessionElapsed.todayDisplayHours(entry, nowMinutes)
        val todayLine = todayHours?.let { "Today: ${HoursCalc.formatHours(it)}" }
        val week =
            "Week ${HoursCalc.formatHours(weekHours)} / ${HoursCalc.formatHours(weekGoalHours)}"
        val openSession = kind == ClockDayState.Kind.OPEN
        return Display(
            statusLine = status,
            todayLine = todayLine,
            weekLine = week,
            openSession = openSession
        )
    }
}
