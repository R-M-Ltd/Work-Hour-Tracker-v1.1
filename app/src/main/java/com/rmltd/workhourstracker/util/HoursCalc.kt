package com.rmltd.workhourstracker.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Clock math. Hours are never typed; they are derived from clock times.
 *
 * Minutes are minutes-from-midnight (0..1439).
 * Times that fall before clock-in are treated as after midnight (overnight).
 * When [equalOutMeansFullDay] is true and out equals in, treat as a full 24h
 * overnight finish (Home clock-out across midnight with matching wall times).
 *
 * Break / lunch is optional and unpaid by default (subtracted from gross).
 * Prefer an explicit lunch start/end pair when both are set; otherwise a
 * [breakDurationMinutes] value is subtracted from the shift (still one shift —
 * not a full clock-out). Paid breaks skip subtraction.
 */
object HoursCalc {

    const val MINUTES_PER_DAY = 24 * 60

    data class Worked(
        val hours: Double,
        val lunchApplied: Boolean,
        val overnight: Boolean,
        /** True when unpaid break duration (not lunch times) was subtracted. */
        val breakDurationApplied: Boolean = false
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
        lunchInMinutes: Int? = null,
        equalOutMeansFullDay: Boolean = false,
        breakDurationMinutes: Int? = null,
        breakPaid: Boolean = false
    ): Double = worked(
        clockInMinutes,
        clockOutMinutes,
        lunchOutMinutes,
        lunchInMinutes,
        equalOutMeansFullDay,
        breakDurationMinutes,
        breakPaid
    ).hours

    fun worked(
        clockInMinutes: Int,
        clockOutMinutes: Int,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null,
        equalOutMeansFullDay: Boolean = false,
        breakDurationMinutes: Int? = null,
        breakPaid: Boolean = false
    ): Worked {
        requireValid(clockInMinutes)
        requireValid(clockOutMinutes)
        val overnight = isOvernight(clockInMinutes, clockOutMinutes, equalOutMeansFullDay)
        val outOnTimeline = expand(clockOutMinutes, clockInMinutes, equalOutMeansFullDay)
        val gross = outOnTimeline - clockInMinutes

        val lunch = usableLunch(clockInMinutes, outOnTimeline, lunchOutMinutes, lunchInMinutes)
        val (net, lunchApplied, breakApplied) = when {
            lunch != null -> {
                val n = (lunch.first - clockInMinutes) + (outOnTimeline - lunch.second)
                Triple(n, true, false)
            }
            shouldApplyBreakDuration(breakDurationMinutes, breakPaid) -> {
                val breakMins = breakDurationMinutes!!.coerceAtLeast(0)
                Triple(max(0, gross - min(breakMins, gross)), false, true)
            }
            else -> Triple(gross, false, false)
        }
        return Worked(
            hours = roundToHundredths(net / 60.0),
            lunchApplied = lunchApplied,
            overnight = overnight,
            breakDurationApplied = breakApplied
        )
    }

    /**
     * Place an unpaid break of [durationMinutes] inside the shift so it can be
     * stored as lunchOut/lunchIn when the user picks a duration instead of times.
     * Prefers a noon start when that window fits; otherwise centers the break.
     */
    fun lunchWindowForBreakDuration(
        clockInMinutes: Int,
        clockOutMinutes: Int,
        durationMinutes: Int,
        equalOutMeansFullDay: Boolean = false
    ): Pair<Int, Int>? {
        if (durationMinutes <= 0) return null
        requireValid(clockInMinutes)
        requireValid(clockOutMinutes)
        val outOnTimeline = expand(clockOutMinutes, clockInMinutes, equalOutMeansFullDay)
        val gross = outOnTimeline - clockInMinutes
        if (durationMinutes >= gross) return null

        val noon = 12 * 60
        val noonOnTimeline = expand(noon, clockInMinutes)
        val noonEnd = noonOnTimeline + durationMinutes
        val (startOnTimeline, endOnTimeline) = if (
            noonOnTimeline > clockInMinutes && noonEnd < outOnTimeline
        ) {
            noonOnTimeline to noonEnd
        } else {
            val mid = clockInMinutes + (gross - durationMinutes) / 2
            mid to (mid + durationMinutes)
        }
        return (startOnTimeline % MINUTES_PER_DAY) to (endOnTimeline % MINUTES_PER_DAY)
    }

    fun isOvernight(
        clockInMinutes: Int,
        clockOutMinutes: Int,
        equalOutMeansFullDay: Boolean = false
    ): Boolean {
        requireValid(clockInMinutes)
        requireValid(clockOutMinutes)
        return clockOutMinutes < clockInMinutes ||
            (equalOutMeansFullDay && clockOutMinutes == clockInMinutes)
    }

    /** Wall-clock label; Locale.US so widget / UI / CSV stay consistent with [formatHours]. */
    fun formatClock(minutesFromMidnight: Int): String {
        val time = LocalTime.of(minutesFromMidnight / 60, minutesFromMidnight % 60)
        return time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
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
        lunchInMinutes: Int?,
        breakDurationMinutes: Int? = null
    ): String? {
        val range = formatRange(clockInMinutes, clockOutMinutes) ?: return null
        return when {
            lunchOutMinutes != null && lunchInMinutes != null ->
                "$range  ·  Break ${formatClock(lunchOutMinutes)} – ${formatClock(lunchInMinutes)}"
            breakDurationMinutes != null && breakDurationMinutes > 0 ->
                "$range  ·  Break ${breakDurationMinutes}m"
            else -> range
        }
    }

    private fun shouldApplyBreakDuration(breakDurationMinutes: Int?, breakPaid: Boolean): Boolean =
        !breakPaid && breakDurationMinutes != null && breakDurationMinutes > 0

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
    private fun expand(time: Int, origin: Int, equalMeansNextDay: Boolean = false): Int = when {
        time < origin -> time + MINUTES_PER_DAY
        equalMeansNextDay && time == origin -> time + MINUTES_PER_DAY
        else -> time
    }

    private fun requireValid(minutes: Int) {
        require(minutes in 0 until MINUTES_PER_DAY) { "minutes out of range: $minutes" }
    }

    private fun roundToHundredths(hours: Double): Double = round(hours * 100.0) / 100.0
}
