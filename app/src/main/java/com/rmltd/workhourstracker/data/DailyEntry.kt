package com.rmltd.workhourstracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    /** Derived from clock-in / clock-out. Never accept this from the UI. */
    val hoursWorked: Double,
    val comments: String = "",
    val weekStartEpochDay: Long,
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    /** Minutes from midnight, 0..1439. Null on pre-1.1 rows. */
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
    val breakPaid: Boolean = false
)
