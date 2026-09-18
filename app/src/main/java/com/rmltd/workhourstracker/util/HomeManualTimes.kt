package com.rmltd.workhourstracker.util

/**
 * Pure helpers for Home "set today's in/out" pickers.
 * No Compose / Room — decisions only; save still goes through [WorkHoursViewModel.saveEntry].
 */
object HomeManualTimes {

    /** Both clocks set and not identical wall times (same rule as Entry Save enablement). */
    fun canSave(clockInMinutes: Int?, clockOutMinutes: Int?): Boolean =
        clockInMinutes != null &&
            clockOutMinutes != null &&
            clockInMinutes != clockOutMinutes

    /** True when out is earlier than in (or equal-out full-day is not used on Home manual save). */
    fun needsOvernightConfirm(clockInMinutes: Int, clockOutMinutes: Int): Boolean =
        HoursCalc.isOvernight(clockInMinutes, clockOutMinutes)

    /**
     * When editing Home times only, keep an existing lunch pair if both sides are present;
     * otherwise pass nulls so save does not invent a partial lunch.
     */
    fun lunchToPreserve(
        existingLunchOut: Int?,
        existingLunchIn: Int?
    ): Pair<Int?, Int?> =
        if (existingLunchOut != null && existingLunchIn != null) {
            existingLunchOut to existingLunchIn
        } else {
            null to null
        }

    /** Default TimePicker seed when the day has no clock yet (matches Entry defaults). */
    fun defaultPickerMinutes(isClockIn: Boolean): Int =
        if (isClockIn) 8 * 60 else 17 * 60
}
