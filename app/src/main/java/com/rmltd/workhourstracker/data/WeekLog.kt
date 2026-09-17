package com.rmltd.workhourstracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A summary record written once per completed week by
 * [com.rmltd.workhourstracker.worker.WeeklyResetWorker] at 2 AM on the configured week-start day.
 * Per-day detail for that week still lives in [DailyEntry] (looked up by
 * [weekStartEpochDay]); this table makes the Log screen's archived-week list
 * fast to read. All-time total is summed from distinct daily [DailyEntry]
 * hours (not from week_logs), so week-start preference changes cannot double-count.
 */
@Entity(tableName = "week_logs")
data class WeekLog(
    @PrimaryKey
    val weekStartEpochDay: Long,
    val weekEndEpochDay: Long,
    val totalHours: Double,
    val archivedAtEpochMillis: Long = System.currentTimeMillis()
)
