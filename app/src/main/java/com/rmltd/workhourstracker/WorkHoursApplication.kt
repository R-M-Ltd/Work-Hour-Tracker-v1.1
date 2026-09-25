package com.rmltd.workhourstracker

import android.app.Application
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.data.WorkHoursDatabase
import com.rmltd.workhourstracker.data.WorkHoursRepository
import com.rmltd.workhourstracker.worker.ReminderScheduler
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
        // Idempotent: re-scheduling just replaces the existing pending alarm.
        ReminderScheduler.scheduleDailyReminder(this)
        ReminderScheduler.scheduleEndOfDayReminder(this)
        ReminderScheduler.scheduleWeeklyReset(this)
        // Catch up archives if week-start 2 AM was missed while the device was off.
        appScope.launch {
            runCatching { repository.catchUpWeekArchives() }
        }
    }
}
