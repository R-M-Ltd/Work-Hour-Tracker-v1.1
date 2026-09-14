package com.rmltd.workhourstracker.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rmltd.workhourstracker.data.DailyEntry
import com.rmltd.workhourstracker.data.ReminderPreferences
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class WorkHoursViewModel(
    private val repository: WorkHoursRepository,
    private val appContext: Context
) : ViewModel() {

    private fun weekStartDay(): DayOfWeek = ReminderPreferences.getWeekStartDay(appContext)

    /** Re-evaluated on [onAppResume] / [notifyPrefsChanged] so week window tracks calendar + prefs. */
    private val weekStartEpoch = MutableStateFlow(
        WeekUtils.weekStartFor(LocalDate.now(), weekStartDay()).toEpochDay()
    )

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
        lunchInMinutes: Int? = null
    ) {
        viewModelScope.launch {
            repository.saveEntry(date, clockInMinutes, clockOutMinutes, comments, lunchOutMinutes, lunchInMinutes)
        }
    }

    fun clockInNow(date: LocalDate = LocalDate.now()) {
        val now = LocalTime.now()
        val minutes = now.hour * 60 + now.minute
        viewModelScope.launch {
            repository.clockInNow(date, minutes)
        }
    }

    /**
     * @param onResult true if saved; false if clock-in was missing or times equal.
     */
    fun clockOutNow(date: LocalDate = LocalDate.now(), onResult: (Boolean) -> Unit = {}) {
        val now = LocalTime.now()
        val minutes = now.hour * 60 + now.minute
        viewModelScope.launch {
            val ok = repository.clockOutNow(date, minutes)
            onResult(ok)
        }
    }

    suspend fun loadEntriesForWeek(weekStart: LocalDate): List<DailyEntry> =
        repository.entriesForWeekOnce(weekStart)

    suspend fun loadExportWeeks(): List<Pair<LocalDate, List<DailyEntry>>> =
        repository.allEntriesForExport()

    /** Call after Settings changes week start or weekly goal (Compose nav does not re-resume Activity). */
    fun notifyPrefsChanged() {
        _weeklyGoalHours.value = ReminderPreferences.getWeeklyGoalHours(appContext)
        _weekStartDay.value = weekStartDay()
        refreshWeekBoundary()
        viewModelScope.launch {
            runCatching { repository.catchUpWeekArchives() }
        }
    }

    /** Call from Activity.onResume so week window and entry query track the calendar. */
    fun onAppResume() {
        refreshWeekBoundary()
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
