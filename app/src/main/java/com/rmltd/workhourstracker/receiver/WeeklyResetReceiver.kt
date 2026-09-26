package com.rmltd.workhourstracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rmltd.workhourstracker.worker.ReminderScheduler
import com.rmltd.workhourstracker.worker.WeeklyResetWorker
import com.rmltd.workhourstracker.widget.WorkHoursWidgetUpdater

class WeeklyResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Hand the DB work off to WorkManager so it reliably runs to completion
        // even if this receiver's process is killed right after the alarm fires.
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<WeeklyResetWorker>().build())
        WorkHoursWidgetUpdater.requestUpdate(context)

        // Re-arm next week-start reset — exact alarms are one-shot.
        ReminderScheduler.scheduleWeeklyReset(context)
    }
}
