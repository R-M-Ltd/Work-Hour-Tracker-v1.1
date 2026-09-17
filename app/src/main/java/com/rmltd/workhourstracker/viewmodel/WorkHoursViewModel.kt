package com.rmltd.workhourstracker.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rmltd.workhourstracker.data.ClockInResult
import com.rmltd.workhourstracker.data.ClockOutResult
import com.rmltd.workhourstracker.data.DailyEntry
import com.rmltd.workhourstracker.data.HomeClockUi
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.data.SaveEntryResult
import com.rmltd.workhourstracker.data.WeekLog
import com.rmltd.workhourstracker.data.WorkHoursRepository
import com.rmltd.workhourstracker.util.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
class WorkHoursViewModel(
    private val repository: WorkHoursRepository,
    private val appContext: Context
) : ViewModel() {

    private fun weekStartDay(): DayOfWeek = ReminderPreferences.getWeekStartDay(appContext)

    /** Re-evaluated on Activity ON_START/resume, DATE_CHANGED broadcasts, and [notifyPrefsChanged]. */
    private val weekStartEpoch = MutableStateFlow(
        WeekUtils.weekStartFor(LocalDate.now(), weekStartDay()).toEpochDay()
    )

    /** Calendar "today" for Home clock UI; refreshed on resume. */
    private val homeAnchorDate = MutableStateFlow(LocalDate.now())

    private val clockFlight = AtomicBoolean(false)
    private val clockFlightMutex = Mutex()

    private val _clockOpInProgress = MutableStateFlow(false)
    val clockOpInProgress: StateFlow<Boolean> = _clockOpInProgress

    val weekStart: StateFlow<LocalDate> = weekStartEpoch
        .map { LocalDate.ofEpochDay(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LocalDate.ofEpochDay(weekStartEpoch.value)
        )

    private val _weeklyGoalHours = MutableStateFlow(ReminderPreferences.getWeeklyGoalHours(appContext))
    val weeklyGoalHours: StateFlow<Double> = _weeklyGoalHours

    private val _weekStartDay = MutableStateFlow(weekStartDay())
    val configuredWeekStartDay: StateFlow<DayOfWeek> = _weekStartDay

    val currentWeekEntries: StateFlow<List<DailyEntry>> = weekStartEpoch
        .flatMapLatest { startEpoch ->
            repository.entriesForWeek(LocalDate.ofEpochDay(startEpoch))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val homeClockUi: StateFlow<HomeClockUi> = homeAnchorDate
        .flatMapLatest { today -> repository.homeClockUi(today) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HomeClockUi(clockInEnabled = true, clockOutEnabled = false, overnightPending = false)
        )

    /** Empty (0.0h) archived weeks are hidden from History. */
    val weekLogs: StateFlow<List<WeekLog>> = repository.visibleWeekLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTimeTotal: StateFlow<Double> = repository.allTimeTotal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun daysInWeek(weekStart: LocalDate): List<LocalDate> = WeekUtils.daysInWeek(weekStart)

    fun entryFor(date: LocalDate, entries: List<DailyEntry>): DailyEntry? =
        entries.firstOrNull { it.dateEpochDay == date.toEpochDay() }

    fun runningTotal(entries: List<DailyEntry>): Double = entries.sumOf { it.hoursWorked }

    fun saveEntry(
        date: LocalDate,
        clockInMinutes: Int,
        clockOutMinutes: Int,
        comments: String,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null,
        onResult: (SaveEntryResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = repository.saveEntry(
                date, clockInMinutes, clockOutMinutes, comments, lunchOutMinutes, lunchInMinutes
            )
            onResult(result)
        }
    }

    fun discardOpenAndSaveEntry(
        date: LocalDate,
        clockInMinutes: Int,
        clockOutMinutes: Int,
        comments: String,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.discardOpenAndSaveEntry(
                date, clockInMinutes, clockOutMinutes, comments, lunchOutMinutes, lunchInMinutes
            )
            onDone()
        }
    }


    /**
     * @param onResult [ClockInResult] so Home can toast / dialog the actual outcome.
     * Ignores overlapping taps while a clock op is in flight (M2).
     */
    fun clockInNow(date: LocalDate = LocalDate.now(), onResult: (ClockInResult) -> Unit = {}) {
        if (!clockFlight.compareAndSet(false, true)) return
        _clockOpInProgress.value = true
        val now = LocalTime.now()
        val minutes = now.hour * 60 + now.minute
        viewModelScope.launch {
            try {
                clockFlightMutex.withLock {
                    val result = repository.clockInNow(date, minutes)
                    onResult(result)
                }
            } finally {
                clockFlight.set(false)
                _clockOpInProgress.value = false
            }
        }
    }

    /**
     * @param onResult [ClockOutResult] — may be overnight finish of yesterday's open shift.
     */
    fun clockOutNow(date: LocalDate = LocalDate.now(), onResult: (ClockOutResult) -> Unit = {}) {
        if (!clockFlight.compareAndSet(false, true)) return
        _clockOpInProgress.value = true
        val now = LocalTime.now()
        val minutes = now.hour * 60 + now.minute
        viewModelScope.launch {
            try {
                clockFlightMutex.withLock {
                    val result = repository.clockOutNow(date, minutes)
                    onResult(result)
                }
            } finally {
                clockFlight.set(false)
                _clockOpInProgress.value = false
            }
        }
    }

    /** Discard yesterday's open punch and clock in today (H2 option 3). */
    fun discardOvernightAndClockIn(
        date: LocalDate = LocalDate.now(),
        onResult: (ClockInResult) -> Unit = {}
    ) {
        if (!clockFlight.compareAndSet(false, true)) return
        _clockOpInProgress.value = true
        val now = LocalTime.now()
        val minutes = now.hour * 60 + now.minute
        viewModelScope.launch {
            try {
                clockFlightMutex.withLock {
                    val result = repository.discardOvernightAndClockIn(date, minutes)
                    onResult(result)
                }
            } finally {
                clockFlight.set(false)
                _clockOpInProgress.value = false
            }
        }
    }

    suspend fun loadEntriesForWeek(weekStart: LocalDate): List<DailyEntry> =
        repository.entriesForWeekOnce(weekStart)

    suspend fun loadExportWeeks(): List<Pair<LocalDate, List<DailyEntry>>> =
        repository.allEntriesForExport()

    /**
     * Call after Settings changes week start or weekly goal (Compose nav does not re-resume Activity).
     * When [weekStartChanged] is true, rebuild week_logs and rewrite daily weekStartEpochDay
     * so overlapping old/new week keys cannot double-count History / all-time.
     */
    fun notifyPrefsChanged(weekStartChanged: Boolean = false) {
        _weeklyGoalHours.value = ReminderPreferences.getWeeklyGoalHours(appContext)
        _weekStartDay.value = weekStartDay()
        refreshWeekBoundary()
        homeAnchorDate.value = LocalDate.now()
        viewModelScope.launch {
            runCatching {
                if (weekStartChanged) {
                    repository.rebuildWeekArchivesForCurrentPreference()
                } else {
                    repository.catchUpWeekArchives()
                }
            }
        }
    }

    /** Refresh week boundary + Home today. Called from ON_START/resume and date/TZ broadcasts (L1). */
    fun onAppResume() {
        refreshWeekBoundary()
        homeAnchorDate.value = LocalDate.now()
        _weeklyGoalHours.value = ReminderPreferences.getWeeklyGoalHours(appContext)
        _weekStartDay.value = weekStartDay()
        viewModelScope.launch {
            runCatching { repository.catchUpWeekArchives() }
        }
    }

    private fun refreshWeekBoundary() {
        val start = WeekUtils.weekStartFor(LocalDate.now(), weekStartDay()).toEpochDay()
        if (weekStartEpoch.value != start) {
            weekStartEpoch.value = start
        }
    }
}

class WorkHoursViewModelFactory(
    private val repository: WorkHoursRepository,
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkHoursViewModel::class.java)) {
            return WorkHoursViewModel(repository, appContext.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
