package com.example.workhourstracker

import android.app.Application
import com.example.workhourstracker.data.WorkHoursDatabase
import com.example.workhourstracker.data.WorkHoursRepository
import com.example.workhourstracker.worker.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WorkHoursApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: WorkHoursRepository by lazy {
        WorkHoursRepository(WorkHoursDatabase.getInstance(this).workHoursDao())
    }

    override fun onCreate() {
        super.onCreate()
        // Idempotent: re-scheduling just replaces the existing pending alarm.
        ReminderScheduler.scheduleDailyReminder(this)
        ReminderScheduler.scheduleWeeklyReset(this)
        // Catch up archives if Wednesday 2 AM was missed while the device was off.
        appScope.launch {
            runCatching { repository.catchUpWeekArchives() }
        }
    }
}
