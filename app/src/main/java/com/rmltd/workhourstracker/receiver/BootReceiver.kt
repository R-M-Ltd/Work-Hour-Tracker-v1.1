package com.rmltd.workhourstracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rmltd.workhourstracker.worker.ReminderScheduler
import com.rmltd.workhourstracker.worker.WeeklyResetWorker
import com.rmltd.workhourstracker.widget.WorkHoursWidgetUpdater

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
            // (or alarms cleared by an app update).
            WorkManager.getInstance(context)
                .enqueue(OneTimeWorkRequestBuilder<WeeklyResetWorker>().build())
            WorkHoursWidgetUpdater.requestUpdate(context)
        }
    }
}
