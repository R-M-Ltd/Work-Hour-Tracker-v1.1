package com.rmltd.workhourstracker.data

import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.util.WeekUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Outcome of one-tap Home clock-in. */
enum class ClockInResult {
    STARTED,
    ALREADY_OPEN,
    ALREADY_CLOSED,
    BLOCKED_OVERNIGHT
}

/** Outcome of one-tap Home clock-out (may finish yesterday overnight). */
enum class ClockOutResult {
    SUCCESS,
    SUCCESS_OVERNIGHT,
    FAILED,
    ALREADY_CLOSED
}

/** Outcome of Entry save when an open overnight exists on another day. */
sealed class SaveEntryResult {
    data object Saved : SaveEntryResult()
    data class BlockedOvernightOpen(val openDate: LocalDate) : SaveEntryResult()
}

/**
 * Home clock button / overnight UI derived from today + yesterday rows.
 * Overnight-pending keeps clock-in enabled so Home can show the resolve dialog (H2).
 */
data class HomeClockUi(
    val clockInEnabled: Boolean,
    val clockOutEnabled: Boolean,
    val overnightPending: Boolean,
    val openOvernightDate: LocalDate? = null
)

class WorkHoursRepository(
    private val dao: WorkHoursDao,
    private val weekStartDay: () -> DayOfWeek = { DayOfWeek.WEDNESDAY }
) {

    private val clockMutex = Mutex()

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

    fun entryForDateFlow(date: LocalDate): Flow<DailyEntry?> =
        dao.entryForDate(date.toEpochDay())

    fun homeClockUi(today: LocalDate = LocalDate.now()): Flow<HomeClockUi> =
        combine(
            dao.entryForDate(today.toEpochDay()),
            dao.entryForDate(today.minusDays(1).toEpochDay())
        ) { todayEntry, yesterdayEntry ->
            deriveHomeClockUi(todayEntry, yesterdayEntry)
        }

    suspend fun findOpenEntry(): DailyEntry? = dao.findOpenEntry()

    /**
     * Saves a closed day (both clocks required by caller).
     * Blocks if another day has an open overnight punch, unless [date] is that open day
     * (finishing it) or [forceAfterDiscard] after discarding the open punch.
     */
    suspend fun saveEntry(
        date: LocalDate,
        clockInMinutes: Int,
        clockOutMinutes: Int,
        comments: String,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null,
        forceAfterDiscard: Boolean = false,
        equalOutMeansFullDay: Boolean = false
    ): SaveEntryResult {
        if (!forceAfterDiscard) {
            val open = dao.findOpenEntry()
            if (open != null && open.dateEpochDay != date.toEpochDay()) {
                return SaveEntryResult.BlockedOvernightOpen(
                    LocalDate.ofEpochDay(open.dateEpochDay)
                )
            }
        }
        upsertClosedEntry(
            date,
            clockInMinutes,
            clockOutMinutes,
            comments,
            lunchOutMinutes,
            lunchInMinutes,
            equalOutMeansFullDay
        )
        return SaveEntryResult.Saved
    }

    /** Unchecked write used by clock-out paths (already inside mutex / state checks). */
    private suspend fun upsertClosedEntry(
        date: LocalDate,
        clockInMinutes: Int,
        clockOutMinutes: Int,
        comments: String,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null,
        equalOutMeansFullDay: Boolean = false
    ) {
        val weekStart = WeekUtils.weekStartFor(date, startDay())
        val lunchOut = if (lunchOutMinutes != null && lunchInMinutes != null) lunchOutMinutes else null
        val lunchIn = if (lunchOutMinutes != null && lunchInMinutes != null) lunchInMinutes else null
        val hours = HoursCalc.hoursWorked(
            clockInMinutes,
            clockOutMinutes,
            lunchOut,
            lunchIn,
            equalOutMeansFullDay = equalOutMeansFullDay
        )
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
     * Deletes an open punch (in set, out null) for [date]. No-op if not open.
     * @return true if a row was removed.
     */
    suspend fun discardOpenPunch(date: LocalDate): Boolean = clockMutex.withLock {
        discardOpenPunchUnlocked(date)
    }

    private suspend fun discardOpenPunchUnlocked(date: LocalDate): Boolean {
        val entry = dao.entryForDateOnce(date.toEpochDay()) ?: return false
        if (entry.clockInMinutes == null || entry.clockOutMinutes != null) return false
        dao.deleteEntry(date.toEpochDay())
        return true
    }

    /**
     * Discard yesterday's open overnight (if any) then clock in today.
     * Single-flight with other clock ops.
     */
    suspend fun discardOvernightAndClockIn(
        date: LocalDate,
        minutes: Int = LocalTime.now().hour * 60 + LocalTime.now().minute
    ): ClockInResult = clockMutex.withLock {
        discardOpenPunchUnlocked(date.minusDays(1))
        clockInNowUnlocked(date, minutes)
    }

    /**
     * Discard any open punch on a different day, then save [date] closed.
     */
    suspend fun discardOpenAndSaveEntry(
        date: LocalDate,
        clockInMinutes: Int,
        clockOutMinutes: Int,
        comments: String,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null
    ): SaveEntryResult = clockMutex.withLock {
        val open = dao.findOpenEntry()
        if (open != null && open.dateEpochDay != date.toEpochDay()) {
            discardOpenPunchUnlocked(LocalDate.ofEpochDay(open.dateEpochDay))
        }
        upsertClosedEntry(
            date,
            clockInMinutes,
            clockOutMinutes,
            comments,
            lunchOutMinutes,
            lunchInMinutes
        )
        SaveEntryResult.Saved
    }

    /**
     * One-tap clock-in for [date] at [minutes] (minutes since midnight).
     * Legal transitions only: Empty → open row (hours 0.0). Never pairs a new
     * in with a leftover out. Open/Closed/overnight-blocked/legacy are no-ops.
     */
    suspend fun clockInNow(
        date: LocalDate,
        minutes: Int = LocalTime.now().hour * 60 + LocalTime.now().minute
    ): ClockInResult = clockMutex.withLock {
        clockInNowUnlocked(date, minutes)
    }

    private suspend fun clockInNowUnlocked(date: LocalDate, minutes: Int): ClockInResult {
        val existing = dao.entryForDateOnce(date.toEpochDay())
        val todayIn = existing?.clockInMinutes
        val todayOut = existing?.clockOutMinutes

        if (todayIn != null && todayOut != null) {
            return ClockInResult.ALREADY_CLOSED
        }
        if (todayIn != null) {
            return ClockInResult.ALREADY_OPEN
        }
        // Legacy hours-only row (migrated): treat as closed — do not wipe.
        if (existing != null && existing.hoursWorked > 0.0) {
            return ClockInResult.ALREADY_CLOSED
        }

        // Empty today: block if yesterday still has an open overnight shift.
        val prior = dao.entryForDateOnce(date.minusDays(1).toEpochDay())
        if (prior != null && prior.clockInMinutes != null && prior.clockOutMinutes == null) {
            return ClockInResult.BLOCKED_OVERNIGHT
        }

        val weekStart = WeekUtils.weekStartFor(date, startDay())
        dao.upsertEntry(
            DailyEntry(
                dateEpochDay = date.toEpochDay(),
                hoursWorked = 0.0,
                comments = existing?.comments.orEmpty(),
                weekStartEpochDay = weekStart.toEpochDay(),
                clockInMinutes = minutes,
                clockOutMinutes = null,
                lunchOutMinutes = existing?.lunchOutMinutes,
                lunchInMinutes = existing?.lunchInMinutes
            )
        )
        return ClockInResult.STARTED
    }

    /**
     * One-tap clock-out for [date] at [minutes].
     * Open today → close today. Empty today + yesterday open → finish overnight.
     * Closed today → no write. Empty with no overnight → FAILED.
     * Overnight finish allows equal wall-clock minutes (24.00h). Same-day close still rejects equal.
     */
    suspend fun clockOutNow(
        date: LocalDate,
        minutes: Int = LocalTime.now().hour * 60 + LocalTime.now().minute
    ): ClockOutResult = clockMutex.withLock {
        val today = dao.entryForDateOnce(date.toEpochDay())
        val todayIn = today?.clockInMinutes
        val todayOut = today?.clockOutMinutes

        if (todayIn != null && todayOut != null) {
            return@withLock ClockOutResult.ALREADY_CLOSED
        }

        if (today != null && todayIn != null) {
            // Same calendar day: still reject identical wall times (0h).
            if (todayIn == minutes) return@withLock ClockOutResult.FAILED
            upsertClosedEntry(
                date = date,
                clockInMinutes = todayIn,
                clockOutMinutes = minutes,
                comments = today.comments,
                lunchOutMinutes = today.lunchOutMinutes,
                lunchInMinutes = today.lunchInMinutes
            )
            return@withLock ClockOutResult.SUCCESS
        }

        val yesterday = date.minusDays(1)
        val prior = dao.entryForDateOnce(yesterday.toEpochDay())
        val priorIn = prior?.clockInMinutes
        if (prior != null && priorIn != null && prior.clockOutMinutes == null) {
            // Equal wall times OK for overnight → 24.00h via HoursCalc.
            upsertClosedEntry(
                date = yesterday,
                clockInMinutes = priorIn,
                clockOutMinutes = minutes,
                comments = prior.comments,
                lunchOutMinutes = prior.lunchOutMinutes,
                lunchInMinutes = prior.lunchInMinutes,
                equalOutMeansFullDay = priorIn == minutes
            )
            return@withLock ClockOutResult.SUCCESS_OVERNIGHT
        }
        return@withLock ClockOutResult.FAILED
    }

    fun allWeekLogs(): Flow<List<WeekLog>> = dao.allWeekLogs()

    /** Week logs with meaningful hours only (empty 0.0 archives are hidden). */
    fun visibleWeekLogs(): Flow<List<WeekLog>> =
        dao.allWeekLogs().map { logs -> logs.filter { it.totalHours > 0.0 } }

    /** Sum distinct daily hours so overlapping week_logs cannot inflate the total. */
    fun allTimeTotal(): Flow<Double> = dao.allTimeDailyTotal().map { it ?: 0.0 }

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
     * After the week-start preference changes: rewrite each daily row's
     * [DailyEntry.weekStartEpochDay], clear overlapping [WeekLog] keys, and
     * rebuild archives from daily rows under the new week definition.
     */
    suspend fun rebuildWeekArchivesForCurrentPreference() {
        val day = startDay()
        val all = dao.allEntries()
        for (entry in all) {
            val recomputed = WeekUtils.weekStartFor(
                LocalDate.ofEpochDay(entry.dateEpochDay),
                day
            ).toEpochDay()
            if (entry.weekStartEpochDay != recomputed) {
                dao.upsertEntry(entry.copy(weekStartEpochDay = recomputed))
            }
        }
        dao.deleteAllWeekLogs()
        catchUpWeekArchives()
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

    companion object {
        fun deriveHomeClockUi(todayEntry: DailyEntry?, yesterdayEntry: DailyEntry?): HomeClockUi {
            val overnightPending = yesterdayEntry != null &&
                yesterdayEntry.clockInMinutes != null &&
                yesterdayEntry.clockOutMinutes == null
            val todayClosed = todayEntry != null &&
                todayEntry.clockInMinutes != null &&
                todayEntry.clockOutMinutes != null
            val todayOpen = todayEntry != null &&
                todayEntry.clockInMinutes != null &&
                todayEntry.clockOutMinutes == null
            val legacyClosed = todayEntry != null &&
                todayEntry.clockInMinutes == null &&
                todayEntry.clockOutMinutes == null &&
                todayEntry.hoursWorked > 0.0

            // Overnight-pending: leave clock-in enabled so tap can show resolve dialog (H2).
            val clockInEnabled = !todayOpen && !todayClosed && !legacyClosed
            val clockOutEnabled = todayOpen || overnightPending

            return HomeClockUi(
                clockInEnabled = clockInEnabled,
                clockOutEnabled = clockOutEnabled,
                overnightPending = overnightPending,
                openOvernightDate = if (overnightPending) {
                    LocalDate.ofEpochDay(yesterdayEntry!!.dateEpochDay)
                } else {
                    null
                }
            )
        }
    }
}
