package com.example.workhourstracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.workhourstracker.data.DailyEntry
import com.example.workhourstracker.data.WeekLog
import com.example.workhourstracker.data.WorkHoursRepository
import com.example.workhourstracker.util.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class WorkHoursViewModel(private val repository: WorkHoursRepository) : ViewModel() {

    /** Re-evaluated on [onAppResume] so a long-lived process crossing Wednesday updates Home. */
    private val weekStartEpoch = MutableStateFlow(WeekUtils.weekStartFor(LocalDate.now()).toEpochDay())

    val weekStart: StateFlow<LocalDate> = weekStartEpoch
        .map { LocalDate.ofEpochDay(it) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LocalDate.ofEpochDay(weekStartEpoch.value)
        )

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

    /** Call from Activity.onResume so week window and entry query track the calendar. */
    fun onAppResume() {
        val start = WeekUtils.weekStartFor(LocalDate.now()).toEpochDay()
        if (weekStartEpoch.value != start) {
            weekStartEpoch.value = start
        }
        viewModelScope.launch {
            runCatching { repository.catchUpWeekArchives() }
        }
    }
}

class WorkHoursViewModelFactory(private val repository: WorkHoursRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkHoursViewModel::class.java)) {
            return WorkHoursViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
    }
}
