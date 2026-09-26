package com.rmltd.workhourstracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rmltd.workhourstracker.worker.ReminderScheduler
import com.rmltd.workhourstracker.worker.WeeklyResetWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            ReminderScheduler.scheduleDailyReminder(context)
            ReminderScheduler.scheduleEndOfDayReminder(context)
            ReminderScheduler.scheduleWeeklyReset(context)
            // Catch up any week archives missed while the device was powered off
            // (or alarms cleared by an app update). Widget refresh runs in
            // WeeklyResetWorker *after* catchUpWeekArchives (not here) so a
            // week-boundary boot does not briefly show pre-archive week totals —
            // same pattern as WeeklyResetReceiver. Non-boundary boots still
            // refresh once the (usually no-op) archive path finishes.
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<WeeklyResetWorker>().build())
        }
    }
}
