package com.rmltd.workhourstracker

import android.app.Application
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.data.WorkHoursDatabase
import com.rmltd.workhourstracker.data.WorkHoursRepository
import com.rmltd.workhourstracker.worker.ReminderScheduler
import com.rmltd.workhourstracker.widget.WorkHoursWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WorkHoursApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: WorkHoursRepository by lazy {
        WorkHoursRepository(
            dao = WorkHoursDatabase.getInstance(this).workHoursDao(),
            weekStartDay = { ReminderPreferences.getWeekStartDay(this) }
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Schedule + archive off main: AlarmManager work is idempotent (replace pending).
        // Once-per-process; BootReceiver / Settings still re-arm when needed.
        appScope.launch {
            ReminderScheduler.scheduleDailyReminder(this@WorkHoursApplication)
            ReminderScheduler.scheduleEndOfDayReminder(this@WorkHoursApplication)
            ReminderScheduler.scheduleWeeklyReset(this@WorkHoursApplication)
            // Catch up archives if week-start 2 AM was missed while the device was off.
            runCatching { repository.catchUpWeekArchives() }
        }
        WorkHoursWidgetUpdater.requestUpdate(this)
    }
}
