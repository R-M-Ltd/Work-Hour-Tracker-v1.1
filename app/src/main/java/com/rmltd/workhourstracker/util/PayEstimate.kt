package com.rmltd.workhourstracker.util

import java.util.Locale
import kotlin.math.max
import kotlin.math.round

/**
 * Pure helpers for Home week-strip overtime and rough pay estimates.
 *
 * Currency is a fixed USD-style `$` prefix with [Locale.US] decimals — no
 * multi-currency support (Phase B). Estimates are not payroll.
 */
object PayEstimate {

    /** True when [hours] exceed a positive [goalHours] target. */
    fun isOvertime(hours: Double, goalHours: Double): Boolean =
        goalHours > 0.0 && hours > goalHours

    /** Hours past the goal, or 0 when not overtime / goal unset. */
    fun overtimeHours(hours: Double, goalHours: Double): Double =
        if (isOvertime(hours, goalHours)) roundToHundredths(hours - goalHours) else 0.0

    fun remainingHours(hours: Double, goalHours: Double): Double =
        if (goalHours <= 0.0) 0.0 else roundToHundredths(max(0.0, goalHours - hours))

    /** Ring fill 0..1; overtime still caps at 1.0 visually. */
    fun progressFraction(hours: Double, goalHours: Double): Float =
        if (goalHours > 0.0) (hours / goalHours).toFloat().coerceIn(0f, 1f) else 0f

    /**
     * Rough pay = hours × rate. Returns null when [hourlyRate] is unset/zero
     * so callers can hide the estimate. Negative hours treated as 0.
     */
    fun roughPay(hours: Double, hourlyRate: Double): Double? {
        if (hourlyRate <= 0.0) return null
        return roundToCents(max(0.0, hours) * hourlyRate)
    }

    /** USD-style `$12.34` (Locale.US). Documented Phase B currency choice. */
    fun formatCurrencyUsd(amount: Double): String =
        "$%.2f".format(Locale.US, amount)

    private fun roundToHundredths(value: Double): Double = round(value * 100.0) / 100.0

    private fun roundToCents(value: Double): Double = round(value * 100.0) / 100.0
}
