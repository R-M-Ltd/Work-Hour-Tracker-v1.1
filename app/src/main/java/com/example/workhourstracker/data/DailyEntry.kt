package com.example.workhourstracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per calendar day. [dateEpochDay] (LocalDate.toEpochDay()) is the primary
 * key, so saving an entry for a day that already has one simply overwrites it.
 *
 * Design note: rows here are never deleted. The "weekly reset" the user asked for
 * is achieved by always querying for the *current* Wed-Tue date window (see
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
    /** Optional lunch start (leave for lunch). Null means no lunch. */
    val lunchOutMinutes: Int? = null,
    /** Optional lunch end (back from lunch). Null means no lunch. */
    val lunchInMinutes: Int? = null
)
