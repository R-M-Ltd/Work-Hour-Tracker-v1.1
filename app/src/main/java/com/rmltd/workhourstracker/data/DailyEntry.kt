package com.rmltd.workhourstracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How [DailyEntry.hoursWorked] was set.
 * CLOCK = derived from clock in/out (default for existing / punch saves).
 * TYPED = user typed hours in Add/Change sheet; typed wins on Save.
 */
enum class HoursSource {
    CLOCK,
    TYPED;

    companion object {
        fun fromStorage(raw: String?): HoursSource =
            when (raw?.uppercase()) {
                "TYPED" -> TYPED
                else -> CLOCK
            }
    }
}

/**
 * One row per calendar day. [dateEpochDay] (LocalDate.toEpochDay()) is the primary
 * key, so saving an entry for a day that already has one simply overwrites it.
 *
 * Design note: rows here are never deleted. The "weekly reset" the user asked for
 * is achieved by always querying for the *current* configured week date window (see
 * [WorkHoursRepository.currentWeekEntries]) rather than wiping data — the Home
 * screen naturally shows a clean week once the calendar turns over, while every
 * entry ever saved remains available to the history log. This avoids any chance
 * of a bug deleting hours before they've been safely archived.
 */
@Entity(tableName = "daily_entries")
data class DailyEntry(
    @PrimaryKey
    val dateEpochDay: Long,
    /** Stored hours — clock-derived or typed (see [hoursSource]). */
    val hoursWorked: Double,
    val comments: String = "",
    val weekStartEpochDay: Long,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    /** Minutes from midnight, 0..1439. Null on pre-1.1 rows / typed-only days. */
    val clockInMinutes: Int? = null,
    val clockOutMinutes: Int? = null,
    /** Optional break/lunch start. Null means no timed break. */
    val lunchOutMinutes: Int? = null,
    /** Optional break/lunch end. Null means no timed break. */
    val lunchInMinutes: Int? = null,
    /**
     * Optional unpaid break length in minutes (Phase A). Used when the user logs
     * a duration instead of break start/end. Timed lunch pair wins when both set.
     */
    val breakDurationMinutes: Int? = null,
    /** When true, [breakDurationMinutes] is not subtracted (paid break). */
    val breakPaid: Boolean = false,
    /**
     * CLOCK (default) or TYPED. Existing rows migrate to CLOCK.
     * Stored as String for Room simplicity.
     */
    val hoursSource: String = HoursSource.CLOCK.name,
    /** Explicit mark that no unpaid lunch/break was taken. Default false. */
    val noLunchTaken: Boolean = false
) {
    fun hoursSourceEnum(): HoursSource = HoursSource.fromStorage(hoursSource)

    /** True when this day has persisted hours (closed punch or typed, incl. 0.00). */
    fun hasPersistedHours(): Boolean =
        hoursSourceEnum() == HoursSource.TYPED ||
            (clockOutMinutes != null && clockInMinutes != null)
}
