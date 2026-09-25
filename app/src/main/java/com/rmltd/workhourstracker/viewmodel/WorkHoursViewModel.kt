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
import com.rmltd.workhourstracker.data.ThemePreferences
import com.rmltd.workhourstracker.ui.theme.AppFontStyle
import com.rmltd.workhourstracker.ui.theme.AppTheme
import com.rmltd.workhourstracker.data.SaveEntryResult
import com.rmltd.workhourstracker.data.WeekLog
import com.rmltd.workhourstracker.data.WorkHoursRepository
import com.rmltd.workhourstracker.util.BackupCodec
import com.rmltd.workhourstracker.util.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

    private val _colorTheme = MutableStateFlow(ThemePreferences.getColorTheme(appContext))
    val colorTheme: StateFlow<AppTheme> = _colorTheme

    private val _fontStyle = MutableStateFlow(ThemePreferences.getFontStyle(appContext))
    val fontStyle: StateFlow<AppFontStyle> = _fontStyle

    private val _weeklyGoalHours = MutableStateFlow(ReminderPreferences.getWeeklyGoalHours(appContext))
    val weeklyGoalHours: StateFlow<Double> = _weeklyGoalHours

    /** 0.0 means unset — Home/History hide rough pay estimate. */
    private val _hourlyRate = MutableStateFlow(ReminderPreferences.getHourlyRate(appContext))
    val hourlyRate: StateFlow<Double> = _hourlyRate

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

    /**
     * Same single-flight mutex / [clockOpInProgress] as clock-in/out so rapid
     * Save + Clock out cannot race at Room. Waits (does not drop) if a clock
     * op holds the mutex; also covered by repository [clockMutex].
     */
    fun saveEntry(
        date: LocalDate,
        clockInMinutes: Int,
        clockOutMinutes: Int,
        comments: String,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null,
        breakDurationMinutes: Int? = null,
        breakPaid: Boolean = false,
        onResult: (SaveEntryResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            clockFlightMutex.withLock {
                _clockOpInProgress.value = true
                try {
                    val result = repository.saveEntry(
                        date,
                        clockInMinutes,
                        clockOutMinutes,
                        comments,
                        lunchOutMinutes,
                        lunchInMinutes,
                        breakDurationMinutes = breakDurationMinutes,
                        breakPaid = breakPaid
                    )
                    onResult(result)
                } finally {
                    _clockOpInProgress.value = false
                }
            }
        }
    }

    fun discardOpenAndSaveEntry(
        date: LocalDate,
        clockInMinutes: Int,
        clockOutMinutes: Int,
        comments: String,
        lunchOutMinutes: Int? = null,
        lunchInMinutes: Int? = null,
        breakDurationMinutes: Int? = null,
        breakPaid: Boolean = false,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            clockFlightMutex.withLock {
                _clockOpInProgress.value = true
                try {
                    repository.discardOpenAndSaveEntry(
                        date,
                        clockInMinutes,
                        clockOutMinutes,
                        comments,
                        lunchOutMinutes,
                        lunchInMinutes,
                        breakDurationMinutes = breakDurationMinutes,
                        breakPaid = breakPaid
                    )
                    onDone()
                } finally {
                    _clockOpInProgress.value = false
                }
            }
        }
    }

    /** Date-scoped once-load for Entry (covers overnight edit outside current week). */
    suspend fun loadEntryForDate(date: LocalDate): DailyEntry? =
        repository.entryForDateOnce(date)

    /** Date-scoped Flow for Entry; prefer [loadEntryForDate] for one-shot seed. */
    fun entryForDate(date: LocalDate): Flow<DailyEntry?> =
        repository.entryForDate(date)


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
        val now = LocalTime.now()
        clockOutAt(date, now.hour * 60 + now.minute, onResult)
    }

    /**
     * Clock out at a user-chosen time (forgot-to-clock-out / TimePicker).
     * Same single-flight guards as [clockOutNow].
     */
    fun clockOutAt(
        date: LocalDate = LocalDate.now(),
        minutes: Int,
        onResult: (ClockOutResult) -> Unit = {}
    ) {
        if (!clockFlight.compareAndSet(false, true)) return
        _clockOpInProgress.value = true
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

    fun updateEntryComments(date: LocalDate, comments: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = repository.updateEntryComments(date, comments)
            onDone(ok)
        }
    }

    suspend fun loadAllEntries(): List<DailyEntry> = repository.allEntriesOnce()

    fun snapshotPrefsForBackup(): BackupCodec.PrefsSnapshot {
        val (rh, rm) = ReminderPreferences.getReminderTime(appContext)
        val (eh, em) = ReminderPreferences.getEndOfDayTime(appContext)
        return BackupCodec.PrefsSnapshot(
            weekStartDay = ReminderPreferences.getWeekStartDay(appContext).value,
            weeklyGoalHours = ReminderPreferences.getWeeklyGoalHours(appContext),
            hourlyRate = ReminderPreferences.getHourlyRate(appContext),
            colorTheme = ThemePreferences.getColorTheme(appContext).key,
            fontStyle = ThemePreferences.getFontStyle(appContext).key,
            reminderEnabled = ReminderPreferences.isReminderEnabled(appContext),
            reminderHour = rh,
            reminderMinute = rm,
            endOfDayEnabled = ReminderPreferences.isEndOfDayEnabled(appContext),
            endOfDayHour = eh,
            endOfDayMinute = em
        )
    }

    suspend fun buildBackupJson(appVersion: String): String {
        val prefs = snapshotPrefsForBackup()
        val entries = repository.allEntriesOnce()
        val weekLogs = repository.allWeekLogsOnce()
        return BackupCodec.encode(prefs, entries, weekLogs, appVersion)
    }

    /**
     * Validates [json], replaces all entries/week logs, applies prefs.
     * Caller should reschedule reminders and refresh UI after success.
     */
    suspend fun restoreFromBackupJson(json: String): BackupCodec.BackupPayload {
        val payload = BackupCodec.decode(json)
        repository.replaceAllFromBackup(payload.entries, payload.weekLogs)
        applyPrefsSnapshot(payload.prefs)
        return payload
    }

    fun applyPrefsSnapshot(prefs: BackupCodec.PrefsSnapshot) {
        ReminderPreferences.setWeekStartDay(
            appContext,
            java.time.DayOfWeek.of(prefs.weekStartDay.coerceIn(1, 7))
        )
        ReminderPreferences.setWeeklyGoalHours(appContext, prefs.weeklyGoalHours)
        ReminderPreferences.setHourlyRate(appContext, prefs.hourlyRate)
        ReminderPreferences.setReminderEnabled(appContext, prefs.reminderEnabled)
        ReminderPreferences.setReminderTime(appContext, prefs.reminderHour, prefs.reminderMinute)
        ReminderPreferences.setEndOfDayEnabled(appContext, prefs.endOfDayEnabled)
        ReminderPreferences.setEndOfDayTime(appContext, prefs.endOfDayHour, prefs.endOfDayMinute)
        ThemePreferences.setColorTheme(
            appContext,
            com.rmltd.workhourstracker.ui.theme.AppTheme.fromKey(prefs.colorTheme)
        )
        ThemePreferences.setFontStyle(
            appContext,
            com.rmltd.workhourstracker.ui.theme.AppFontStyle.fromKey(prefs.fontStyle)
        )
        _colorTheme.value = ThemePreferences.getColorTheme(appContext)
        _fontStyle.value = ThemePreferences.getFontStyle(appContext)
        notifyPrefsChanged(weekStartChanged = true)
    }


    /**
     * Call after Settings changes week start or weekly goal (Compose nav does not re-resume Activity).
     * When [weekStartChanged] is true, rebuild week_logs and rewrite daily weekStartEpochDay
     * so overlapping old/new week keys cannot double-count History / all-time.
     */
    fun setColorTheme(theme: AppTheme) {
        ThemePreferences.setColorTheme(appContext, theme)
        _colorTheme.value = theme
    }

    fun setFontStyle(style: AppFontStyle) {
        ThemePreferences.setFontStyle(appContext, style)
        _fontStyle.value = style
    }

    fun notifyPrefsChanged(weekStartChanged: Boolean = false) {
        _weeklyGoalHours.value = ReminderPreferences.getWeeklyGoalHours(appContext)
        _hourlyRate.value = ReminderPreferences.getHourlyRate(appContext)
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
        _hourlyRate.value = ReminderPreferences.getHourlyRate(appContext)
        _weekStartDay.value = weekStartDay()
        _colorTheme.value = ThemePreferences.getColorTheme(appContext)
        _fontStyle.value = ThemePreferences.getFontStyle(appContext)
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
