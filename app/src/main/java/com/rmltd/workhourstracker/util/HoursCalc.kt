package com.rmltd.workhourstracker.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round

/**
 * Clock math. Hours are never typed; they are derived from clock times.
 *
 * Minutes are minutes-from-midnight (0..1439).
 * Times that fall before clock-in are treated as after midnight (overnight).
 *
 * Lunch is optional. If either lunch time is missing, lunch did not occur.
 */
object HoursCalc {

    const val MINUTES_PER_DAY = 24 * 60

    data class Worked(
        val hours: Double,
        val lunchApplied: Boolean,
        val overnight: Boolean
    )

    fun durationMinutes(clockInMinutes: Int, clockOutMinutes: Int): Int {
        requireValid(clockInMinutes)
        requireValid(clockOutMinutes)
        return expand(clockOutMinutes, clockInMinutes) - clockInMinutes
    }

    fun hoursWorked(
        clockInMinutes: Int,
        clockOutMinutes: Int,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null
    ): Double = worked(clockInMinutes, clockOutMinutes, lunchOutMinutes, lunchInMinutes).hours

    fun worked(
        clockInMinutes: Int,
        clockOutMinutes: Int,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null
    ): Worked {
        requireValid(clockInMinutes)
        requireValid(clockOutMinutes)
        val outOnTimeline = expand(clockOutMinutes, clockInMinutes)
        val overnight = clockOutMinutes < clockInMinutes
        val gross = outOnTimeline - clockInMinutes

        val lunch = usableLunch(clockInMinutes, outOnTimeline, lunchOutMinutes, lunchInMinutes)
        val net = if (lunch != null) {
            (lunch.first - clockInMinutes) + (outOnTimeline - lunch.second)
        } else {
            gross
        }
        return Worked(
            hours = roundToHundredths(net / 60.0),
            lunchApplied = lunch != null,
            overnight = overnight
        )
    }

    fun isOvernight(clockInMinutes: Int, clockOutMinutes: Int): Boolean =
        clockOutMinutes < clockInMinutes

    fun formatClock(minutesFromMidnight: Int): String {
        val time = LocalTime.of(minutesFromMidnight / 60, minutesFromMidnight % 60)
        return time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }

    fun formatHours(hours: Double): String = "%.2fh".format(Locale.US, hours)

    fun formatRange(clockInMinutes: Int?, clockOutMinutes: Int?): String? {
        if (clockInMinutes == null || clockOutMinutes == null) return null
        return "${formatClock(clockInMinutes)} – ${formatClock(clockOutMinutes)}"
    }

    fun formatDayLabel(
        clockInMinutes: Int?,
        clockOutMinutes: Int?,
        lunchOutMinutes: Int?,
        lunchInMinutes: Int?
    ): String? {
        val range = formatRange(clockInMinutes, clockOutMinutes) ?: return null
        return if (lunchOutMinutes != null && lunchInMinutes != null) {
            "$range  ·  Lunch ${formatClock(lunchOutMinutes)} – ${formatClock(lunchInMinutes)}"
        } else {
            range
        }
    }

    private fun usableLunch(
        clockIn: Int,
        clockOutOnTimeline: Int,
        lunchOutMinutes: Int?,
        lunchInMinutes: Int?
    ): Pair<Int, Int>? {
        if (lunchOutMinutes == null || lunchInMinutes == null) return null
        requireValid(lunchOutMinutes)
        requireValid(lunchInMinutes)
        val lunchOut = expand(lunchOutMinutes, clockIn)
        val lunchIn = expand(lunchInMinutes, clockIn)
        if (lunchIn <= lunchOut) return null
        if (lunchOut <= clockIn || lunchIn >= clockOutOnTimeline) return null
        return lunchOut to lunchIn
    }

    /** Map a clock time onto the timeline that starts at [origin] (clock-in). */
    private fun expand(time: Int, origin: Int): Int =
        if (time < origin) time + MINUTES_PER_DAY else time

    private fun requireValid(minutes: Int) {
        require(minutes in 0 until MINUTES_PER_DAY) { "minutes out of range: $minutes" }
    }

    private fun roundToHundredths(hours: Double): Double = round(hours * 100.0) / 100.0
}
