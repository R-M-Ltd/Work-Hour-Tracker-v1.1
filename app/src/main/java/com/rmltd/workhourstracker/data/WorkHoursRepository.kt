package com.rmltd.workhourstracker.data

import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.util.WeekUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class WorkHoursRepository(
    private val dao: WorkHoursDao,
    private val weekStartDay: () -> DayOfWeek = { DayOfWeek.WEDNESDAY }
) {

    private fun startDay(): DayOfWeek = weekStartDay()

    fun currentWeekEntries(today: LocalDate = LocalDate.now()): Flow<List<DailyEntry>> {
        val weekStart = WeekUtils.weekStartFor(today, startDay())
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

    suspend fun entryForDateOnce(date: LocalDate): DailyEntry? =
        dao.entryForDateOnce(date.toEpochDay())

    suspend fun saveEntry(
        date: LocalDate,
        clockInMinutes: Int,
        clockOutMinutes: Int,
        comments: String,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null
    ) {
        val weekStart = WeekUtils.weekStartFor(date, startDay())
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

    /**
     * One-tap clock-in for [date] at [minutes] (minutes since midnight).
     * Creates a new row if needed; does not invent lunch. Hours stay 0 until clock-out.
     */
    suspend fun clockInNow(date: LocalDate, minutes: Int = LocalTime.now().hour * 60 + LocalTime.now().minute) {
        val weekStart = WeekUtils.weekStartFor(date, startDay())
        val existing = dao.entryForDateOnce(date.toEpochDay())
        val clockOut = existing?.clockOutMinutes
        val lunchOut = existing?.lunchOutMinutes
        val lunchIn = existing?.lunchInMinutes
        val hours = if (clockOut != null && clockOut != minutes) {
            HoursCalc.hoursWorked(minutes, clockOut, lunchOut, lunchIn)
        } else {
            0.0
        }
        dao.upsertEntry(
            DailyEntry(
                dateEpochDay = date.toEpochDay(),
                hoursWorked = hours,
                comments = existing?.comments.orEmpty(),
                weekStartEpochDay = weekStart.toEpochDay(),
                clockInMinutes = minutes,
                clockOutMinutes = if (clockOut != null && clockOut == minutes) null else clockOut,
                lunchOutMinutes = lunchOut,
                lunchInMinutes = lunchIn
            )
        )
    }

    /**
     * One-tap clock-out for [date] at [minutes]. Requires an existing clock-in.
     * Preserves lunch/comments if already set; does not invent lunch.
     * @return false if there is no clock-in yet.
     */
    suspend fun clockOutNow(
        date: LocalDate,
        minutes: Int = LocalTime.now().hour * 60 + LocalTime.now().minute
    ): Boolean {
        val existing = dao.entryForDateOnce(date.toEpochDay())
        val clockIn = existing?.clockInMinutes ?: return false
        if (clockIn == minutes) return false
        saveEntry(
            date = date,
            clockInMinutes = clockIn,
            clockOutMinutes = minutes,
            comments = existing.comments,
            lunchOutMinutes = existing.lunchOutMinutes,
            lunchInMinutes = existing.lunchInMinutes
        )
        return true
    }

    fun allWeekLogs(): Flow<List<WeekLog>> = dao.allWeekLogs()

    /** Week logs with meaningful hours only (empty 0.0 archives are hidden). */
    fun visibleWeekLogs(): Flow<List<WeekLog>> =
        dao.allWeekLogs().map { logs -> logs.filter { it.totalHours > 0.0 } }

    fun allTimeTotal(): Flow<Double> = dao.allTimeArchivedTotal().map { it ?: 0.0 }

    /**
     * Archives the week immediately before [referenceWeekStart] (defaults to the
     * current week, so by default this archives the week that just ended).
     * Idempotent: safe to call more than once for the same week.
     * Empty weeks (0.0 hours) are not inserted; existing rows get their total refreshed.
     */
    suspend fun archivePreviousWeekIfNeeded(
        referenceWeekStart: LocalDate = WeekUtils.weekStartFor(LocalDate.now(), startDay())
    ) {
        val completedWeekStart = WeekUtils.previousWeekStart(referenceWeekStart)
        upsertWeekArchive(completedWeekStart)
    }

    /**
     * Ensures history is not permanently skipped when the week-start 2 AM alarm
     * was missed (device off, exact-alarm denied, etc.). Always tries the week
     * that just ended, then backfills any older weeks that still have daily
     * rows but no [WeekLog] (or refreshes stale totals).
     */
    suspend fun catchUpWeekArchives() {
        val currentWeekStart = WeekUtils.weekStartFor(LocalDate.now(), startDay())
        archivePreviousWeekIfNeeded(currentWeekStart)

        val pending = dao.weekStartsWithEntriesBefore(currentWeekStart.toEpochDay())
        for (startEpoch in pending) {
            upsertWeekArchive(LocalDate.ofEpochDay(startEpoch))
        }
    }

    /**
     * Insert a week summary when total &gt; 0; skip empty weeks.
     * If a row already exists, update its total (mitigates IGNORE staleness).
     * If total is 0 and a row exists, leave it — History UI filters zeros out.
     */
    private suspend fun upsertWeekArchive(completedWeekStart: LocalDate) {
        val startEpoch = completedWeekStart.toEpochDay()
        val weekEndEpoch = WeekUtils.weekEndFor(completedWeekStart).toEpochDay()
        val entries = entriesForWeekOnce(completedWeekStart)
        val total = entries.sumOf { it.hoursWorked }

        if (dao.weekLogExists(startEpoch)) {
            dao.updateWeekLog(
                startEpochDay = startEpoch,
                weekEndEpochDay = weekEndEpoch,
                totalHours = total
            )
            return
        }
        if (total <= 0.0) return
        dao.insertWeekLog(
            WeekLog(
                weekStartEpochDay = startEpoch,
                weekEndEpochDay = weekEndEpoch,
                totalHours = total
            )
        )
    }

    /**
     * All daily rows grouped by the *configured* week-start preference
     * (recomputed from each row's date, not the stored weekStartEpochDay).
     */
    suspend fun allEntriesForExport(): List<Pair<LocalDate, List<DailyEntry>>> {
        val day = startDay()
        val all = dao.allEntries()
        if (all.isEmpty()) return emptyList()
        return all
            .groupBy { WeekUtils.weekStartFor(LocalDate.ofEpochDay(it.dateEpochDay), day) }
            .toSortedMap()
            .map { (start, entries) -> start to entries.sortedBy { it.dateEpochDay } }
    }
}
