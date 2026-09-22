package com.rmltd.workhourstracker.util

/**
 * Pure helpers for seeding Entry screen fields from a loaded day row.
 * Date-scoped Room load (not the current-week list) is the source of truth so
 * overnight "Edit yesterday" across a week boundary still shows stored clocks.
 */
object EntryFormSeed {

    data class Fields(
        val clockInMinutes: Int?,
        val clockOutMinutes: Int?,
        val lunchOutMinutes: Int?,
        val lunchInMinutes: Int?,
        val comments: String
    )

    fun empty(): Fields = Fields(
        clockInMinutes = null,
        clockOutMinutes = null,
        lunchOutMinutes = null,
        lunchInMinutes = null,
        comments = ""
    )

    fun fromLoaded(
        clockInMinutes: Int?,
        clockOutMinutes: Int?,
        lunchOutMinutes: Int?,
        lunchInMinutes: Int?,
        comments: String?
    ): Fields = Fields(
        clockInMinutes = clockInMinutes,
        clockOutMinutes = clockOutMinutes,
        lunchOutMinutes = lunchOutMinutes,
        lunchInMinutes = lunchInMinutes,
        comments = comments.orEmpty()
    )

    /**
     * Prefer [dateScoped] when its epoch matches [targetEpochDay].
     * Never invent fields from a week-list membership alone — out-of-week
     * overnight edit must come from a date-scoped Room read.
     */
    fun preferDateScoped(
        targetEpochDay: Long,
        dateScopedEpochDay: Long?,
        dateScopedFields: Fields?
    ): Fields {
        if (dateScopedEpochDay != null &&
            dateScopedEpochDay == targetEpochDay &&
            dateScopedFields != null
        ) {
            return dateScopedFields
        }
        return empty()
    }

    /** True when [targetEpochDay] is outside [[weekStartEpoch], [weekEndEpoch]] inclusive. */
    fun isOutsideConfiguredWeek(
        targetEpochDay: Long,
        weekStartEpoch: Long,
        weekEndEpoch: Long
    ): Boolean = targetEpochDay < weekStartEpoch || targetEpochDay > weekEndEpoch
}
