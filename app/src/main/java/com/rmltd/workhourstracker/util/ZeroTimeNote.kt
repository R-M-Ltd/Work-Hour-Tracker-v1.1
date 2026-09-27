package com.rmltd.workhourstracker.util

/**
 * Pure helpers for UI Pack 1.3.26 “zero time → reason note”.
 *
 * Confirming 12:00 AM (`minutes == 0`) on a Home/Entry punch field requires a
 * short reason appended into [com.rmltd.workhourstracker.data.DailyEntry.comments].
 * Cancel must leave the field unchanged (never apply 0).
 */
object ZeroTimeNote {

    const val DIALOG_TITLE = "Reason for 12:00 AM?"
    const val DIALOG_LABEL = "Reason"
    const val DIALOG_PLACEHOLDER = "e.g. Overnight start, site opened early"
    const val CONFIRM_LABEL = "Save reason"
    const val DISMISS_LABEL = "Cancel"
    const val EMPTY_ERROR = "Enter a reason to continue"

    const val ZERO_HOURS_TITLE = "Reason for 0 hours?"
    const val ZERO_HOURS_BODY =
        "This day would save as 0 hours. Add a short reason — it is saved as today’s note."

    /** True when [minutes] is exactly midnight (0 minutes from midnight). */
    fun needsReason(minutes: Int?): Boolean = minutes != null && minutes == 0

    fun anyNeedsReason(
        clockInMinutes: Int? = null,
        clockOutMinutes: Int? = null,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null
    ): Boolean =
        needsReason(clockInMinutes) ||
            needsReason(clockOutMinutes) ||
            needsReason(lunchOutMinutes) ||
            needsReason(lunchInMinutes)

    fun dialogBody(fieldLabel: String): String =
        "You set $fieldLabel to 12:00 AM. Add a short reason — it is saved as today’s note."

    /**
     * Pack append format: `12:00 AM ({fieldLabel}): {reason}` after any existing
     * note (newline). Does not wipe prior text.
     */
    fun formatReasonLine(fieldLabel: String, reason: String): String =
        "12:00 AM ($fieldLabel): ${reason.trim()}"

    fun mergeReasonIntoNote(existing: String, fieldLabel: String, reason: String): String {
        val line = formatReasonLine(fieldLabel, reason)
        if (reason.trim().isEmpty()) return existing
        val e = existing.trim()
        if (e.isEmpty()) return line
        if (e.contains(line) || e.contains(reason.trim())) return e
        return "$e\n$line"
    }

    /** Legacy overload used when field label is already baked into [reason]. */
    fun mergeReasonIntoNote(existing: String, reason: String): String {
        val r = reason.trim()
        if (r.isEmpty()) return existing
        val e = existing.trim()
        if (e.isEmpty()) return r
        if (e.contains(r)) return e
        return "$e\n$r"
    }

    fun canSaveWithNote(
        note: String,
        clockInMinutes: Int? = null,
        clockOutMinutes: Int? = null,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null
    ): Boolean {
        if (!anyNeedsReason(clockInMinutes, clockOutMinutes, lunchOutMinutes, lunchInMinutes)) {
            return true
        }
        return note.trim().isNotEmpty()
    }

    /** Secondary gate: Save would store 0.00h with both clocks set and unequal. */
    fun needsZeroHoursReason(
        clockInMinutes: Int?,
        clockOutMinutes: Int?,
        hoursWorked: Double?
    ): Boolean {
        if (clockInMinutes == null || clockOutMinutes == null) return false
        if (clockInMinutes == clockOutMinutes) return false
        return hoursWorked != null && hoursWorked == 0.0
    }
}
