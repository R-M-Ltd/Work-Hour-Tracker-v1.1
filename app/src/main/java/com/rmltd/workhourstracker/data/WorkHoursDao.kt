package com.rmltd.workhourstracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkHoursDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEntry(entry: DailyEntry)

    @Query("DELETE FROM daily_entries WHERE dateEpochDay = :epochDay")
    suspend fun deleteEntry(epochDay: Long)

    @Query("SELECT * FROM daily_entries WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY dateEpochDay ASC")
    fun entriesForWeek(startEpochDay: Long, endEpochDay: Long): Flow<List<DailyEntry>>

    @Query("SELECT * FROM daily_entries WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY dateEpochDay ASC")
    suspend fun entriesForWeekOnce(startEpochDay: Long, endEpochDay: Long): List<DailyEntry>

    @Query("SELECT * FROM daily_entries ORDER BY dateEpochDay ASC")
    suspend fun allEntries(): List<DailyEntry>

    @Query("SELECT * FROM daily_entries WHERE dateEpochDay = :epochDay LIMIT 1")
    fun entryForDate(epochDay: Long): Flow<DailyEntry?>

    @Query("SELECT * FROM daily_entries WHERE dateEpochDay = :epochDay LIMIT 1")
    suspend fun entryForDateOnce(epochDay: Long): DailyEntry?

    /** Open punch: clock-in set, no clock-out (overnight or same-day open). */
    @Query(
        "SELECT * FROM daily_entries WHERE clockInMinutes IS NOT NULL AND clockOutMinutes IS NULL " +
            "ORDER BY dateEpochDay DESC LIMIT 1"
    )
    suspend fun findOpenEntry(): DailyEntry?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWeekLog(weekLog: WeekLog): Long

    @Query(
        "UPDATE week_logs SET totalHours = :totalHours, weekEndEpochDay = :weekEndEpochDay, " +
            "archivedAtEpochMillis = :archivedAtEpochMillis WHERE weekStartEpochDay = :startEpochDay"
    )
    suspend fun updateWeekLog(
        startEpochDay: Long,
        weekEndEpochDay: Long,
        totalHours: Double,
        archivedAtEpochMillis: Long = System.currentTimeMillis()
    )

    @Query("SELECT EXISTS(SELECT 1 FROM week_logs WHERE weekStartEpochDay = :startEpochDay)")
    suspend fun weekLogExists(startEpochDay: Long): Boolean

    @Query("SELECT * FROM week_logs ORDER BY weekStartEpochDay DESC")
    fun allWeekLogs(): Flow<List<WeekLog>>

    @Query("SELECT SUM(totalHours) FROM week_logs")
    fun allTimeArchivedTotal(): Flow<Double?>

    /** Distinct daily hours — avoids double-counting overlapping week_logs after week-start changes. */
    @Query("SELECT SUM(hoursWorked) FROM daily_entries")
    fun allTimeDailyTotal(): Flow<Double?>

    @Query("DELETE FROM week_logs")
    suspend fun deleteAllWeekLogs()

    /** Distinct week starts that have daily rows and are strictly before [beforeEpochDay]. */
    @Query(
        "SELECT DISTINCT weekStartEpochDay FROM daily_entries " +
            "WHERE weekStartEpochDay < :beforeEpochDay ORDER BY weekStartEpochDay ASC"
    )
    suspend fun weekStartsWithEntriesBefore(beforeEpochDay: Long): List<Long>

    @Query("DELETE FROM daily_entries")
    suspend fun deleteAllEntries()

    @Query("SELECT * FROM week_logs ORDER BY weekStartEpochDay ASC")
    suspend fun allWeekLogsOnce(): List<WeekLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeekLog(weekLog: WeekLog)

}
