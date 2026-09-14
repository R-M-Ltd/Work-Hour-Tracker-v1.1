package com.example.workhourstracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.workhourstracker.worker.ReminderScheduler
import com.example.workhourstracker.worker.WeeklyResetWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleDailyReminder(context)
            ReminderScheduler.scheduleWeeklyReset(context)
            // Catch up any week archives missed while the device was powered off.
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<WeeklyResetWorker>().build())
        }
    }
}
