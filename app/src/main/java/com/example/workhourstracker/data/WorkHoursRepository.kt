package com.example.workhourstracker.data

import com.example.workhourstracker.util.HoursCalc
import com.example.workhourstracker.util.WeekUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class WorkHoursRepository(private val dao: WorkHoursDao) {

    fun currentWeekEntries(today: LocalDate = LocalDate.now()): Flow<List<DailyEntry>> {
        val weekStart = WeekUtils.weekStartFor(today)
        val weekEnd = WeekUtils.weekEndFor(weekStart)
        return dao.entriesForWeek(weekStart.toEpochDay(), weekEnd.toEpochDay())
    }

    fun entriesForWeek(weekStart: LocalDate): Flow<List<DailyEntry>> {
        val weekEnd = WeekUtils.weekEndFor(weekStart)
        return dao.entriesForWeek(weekStart.toEpochDay(), weekEnd.toEpochDay())
    }

    suspend fun entriesForWeekOnce(weekStart: LocalDate): List<DailyEntry> {
        val weekEnd = WeekUtils.weekEndFor(weekStart)
        return dao.entriesForWeekOnce(weekStart.toEpochDay(), weekEnd.toEpochDay())
    }

    suspend fun saveEntry(
        date: LocalDate,
        clockInMinutes: Int,
        clockOutMinutes: Int,
        comments: String,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null
    ) {
        val weekStart = WeekUtils.weekStartFor(date)
        val lunchOut = if (lunchOutMinutes != null && lunchInMinutes != null) lunchOutMinutes else null
        val lunchIn = if (lunchOutMinutes != null && lunchInMinutes != null) lunchInMinutes else null
        val hours = HoursCalc.hoursWorked(clockInMinutes, clockOutMinutes, lunchOut, lunchIn)
        dao.upsertEntry(
            DailyEntry(
                dateEpochDay = date.toEpochDay(),
                hoursWorked = hours,
                comments = comments,
                weekStartEpochDay = weekStart.toEpochDay(),
                clockInMinutes = clockInMinutes,
                clockOutMinutes = clockOutMinutes,
                lunchOutMinutes = lunchOut,
                lunchInMinutes = lunchIn
            )
        )
    }

    fun allWeekLogs(): Flow<List<WeekLog>> = dao.allWeekLogs()

    fun allTimeTotal(): Flow<Double> = dao.allTimeArchivedTotal().map { it ?: 0.0 }

    /**
     * Archives the week immediately before [referenceWeekStart] (defaults to the
     * current week, so by default this archives the week that just ended).
     * Idempotent: safe to call more than once for the same week, e.g. if the
     * device rebooted mid-archive and [com.example.workhourstracker.receiver.BootReceiver]
     * re-triggers it.
     */
    suspend fun archivePreviousWeekIfNeeded(
        referenceWeekStart: LocalDate = WeekUtils.weekStartFor(LocalDate.now())
    ) {
        val completedWeekStart = WeekUtils.previousWeekStart(referenceWeekStart)
        val startEpoch = completedWeekStart.toEpochDay()

        if (dao.weekLogExists(startEpoch)) return

        val entries = entriesForWeekOnce(completedWeekStart)
        val total = entries.sumOf { it.hoursWorked }
        dao.insertWeekLog(
            WeekLog(
                weekStartEpochDay = startEpoch,
                weekEndEpochDay = WeekUtils.weekEndFor(completedWeekStart).toEpochDay(),
                totalHours = total
            )
        )
    }

    /**
     * Ensures history is not permanently skipped when the Wednesday 2 AM alarm
     * was missed (device off, exact-alarm denied, etc.). Always tries the week
     * that just ended, then backfills any older weeks that still have daily
     * rows but no [WeekLog].
     */
    suspend fun catchUpWeekArchives() {
        val currentWeekStart = WeekUtils.weekStartFor(LocalDate.now())
        archivePreviousWeekIfNeeded(currentWeekStart)

        val pending = dao.weekStartsWithEntriesBefore(currentWeekStart.toEpochDay())
        for (startEpoch in pending) {
            if (dao.weekLogExists(startEpoch)) continue
            val completedWeekStart = LocalDate.ofEpochDay(startEpoch)
            val entries = entriesForWeekOnce(completedWeekStart)
            dao.insertWeekLog(
                WeekLog(
                    weekStartEpochDay = startEpoch,
                    weekEndEpochDay = WeekUtils.weekEndFor(completedWeekStart).toEpochDay(),
                    totalHours = entries.sumOf { it.hoursWorked }
                )
            )
        }
    }
}
