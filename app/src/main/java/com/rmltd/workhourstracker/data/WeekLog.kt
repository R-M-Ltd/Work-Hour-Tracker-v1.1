package com.rmltd.workhourstracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A summary record written once per completed week by
 * [com.rmltd.workhourstracker.worker.WeeklyResetWorker] at 2 AM on the configured week-start day.
 * Per-day detail for that week still lives in [DailyEntry] (looked up by
 * [weekStartEpochDay]); this table just makes the Log screen's list and
 * all-time total fast to read without re-summing every entry ever saved.
 */
@Entity(tableName = "week_logs")
data class WeekLog(
    @PrimaryKey
    val weekStartEpochDay: Long,
    val weekEndEpochDay: Long,
    val totalHours: Double,
    val archivedAtEpochMillis: Long = System.currentTimeMillis()
)
