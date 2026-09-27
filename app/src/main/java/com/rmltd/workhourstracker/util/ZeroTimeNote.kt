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

    /** S-B: honesty caption when Home draft is pending Save. */
    const val UNSAVED_DRAFT_CAPTION = "Not saved yet — tap Save today's times"

    /** S-B: one-shot toast when durable draft is restored after process death. */
    const val RESTORED_UNSAVED_TIMES_TOAST = "Restored unsaved times"

    const val ZERO_HOURS_TITLE = "Reason for 0 hours?"
    const val ZERO_HOURS_BODY =
        "This day would save as 0 hours. Add a short reason — it is saved as today’s note."

    /** Optional honesty caption when preview hours are 0.00 with both clocks set. */
    const val ZERO_HOURS_SAVE_CAPTION = "0.00h — Save will ask for a short reason"

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

    /**
     * True when [existing] has a newline-delimited line equal to [line]
     * (trimmed). Substring / prefix matches on a longer line do **not** count
     * (e.g. "…: early bird" must not suppress appending "…: early").
     */
    fun containsFullLine(existing: String, line: String): Boolean {
        val target = line.trim()
        if (target.isEmpty()) return false
        return existing.lineSequence().any { it.trim() == target }
    }

    /**
     * Merge a midnight reason line into [existing].
     * Dedupes only on **full formatted line equality** — never substring
     * [String.contains] and never bare [reason] (avoids skipping append when
     * "early" already appears inside a longer note line).
     */
    fun mergeReasonIntoNote(existing: String, fieldLabel: String, reason: String): String {
        val line = formatReasonLine(fieldLabel, reason)
        if (reason.trim().isEmpty()) return existing
        val e = existing.trim()
        if (e.isEmpty()) return line
        if (containsFullLine(e, line)) return e
        return "$e\n$line"
    }

    /**
     * Legacy overload used when field label is already baked into [reason]
     * (full line or free-text). Dedupes only on full-line equality of the
     * trimmed [reason] — callers should pass the formatted line, not a short
     * substring.
     */
    fun mergeReasonIntoNote(existing: String, reason: String): String {
        val r = reason.trim()
        if (r.isEmpty()) return existing
        val e = existing.trim()
        if (e.isEmpty()) return r
        if (containsFullLine(e, r)) return e
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


    /** Pack append format for the secondary 0-hours save gate. */
    fun formatZeroHoursLine(reason: String): String =
        "0 hours: ${reason.trim()}"

    fun mergeZeroHoursIntoNote(existing: String, reason: String): String {
        val line = formatZeroHoursLine(reason)
        if (reason.trim().isEmpty()) return existing
        val e = existing.trim()
        if (e.isEmpty()) return line
        if (containsFullLine(e, line)) return e
        return "$e\n$line"
    }

    /**
     * Secondary gate: Save would store 0.00h with both clocks set.
     * True for intentional equal in/out (zero day) OR break-eats-shift (in≠out, hours 0).
     */
    fun needsZeroHoursReason(
        clockInMinutes: Int?,
        clockOutMinutes: Int?,
        hoursWorked: Double?
    ): Boolean {
        if (clockInMinutes == null || clockOutMinutes == null) return false
        if (clockInMinutes == clockOutMinutes) return true
        return hoursWorked != null && hoursWorked == 0.0
    }
}
