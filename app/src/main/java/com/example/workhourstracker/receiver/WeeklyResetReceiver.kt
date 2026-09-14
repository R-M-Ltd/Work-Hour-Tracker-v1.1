package com.example.workhourstracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.workhourstracker.worker.ReminderScheduler
import com.example.workhourstracker.worker.WeeklyResetWorker

class WeeklyResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Hand the DB work off to WorkManager so it reliably runs to completion
        // even if this receiver's process is killed right after the alarm fires.
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WeeklyResetWorker>().build())

        // Re-arm next Wednesday's reset — exact alarms are one-shot.
        ReminderScheduler.scheduleWeeklyReset(context)
    }
}
