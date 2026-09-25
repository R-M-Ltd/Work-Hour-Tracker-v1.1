package com.rmltd.workhourstracker.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rmltd.workhourstracker.WorkHoursApplication
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.worker.ReminderScheduler
import com.rmltd.workhourstracker.widget.WorkHoursWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Handles end-of-day notification actions: clock out now, or extend (snooze 1h).
 */
class EndOfDayActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(EndOfDayReminderReceiver.NOTIFICATION_ID)

        when (action) {
            ACTION_CLOCK_OUT -> {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val app = context.applicationContext as? WorkHoursApplication
                        val repo = app?.repository
                        if (repo != null) {
                            val now = LocalTime.now()
                            val minutes = now.hour * 60 + now.minute
                            repo.clockOutNow(LocalDate.now(), minutes)
                            WorkHoursWidgetUpdater.requestUpdate(context)
                        }
                        ReminderPreferences.clearEndOfDaySnooze(context)
                        ReminderScheduler.scheduleEndOfDayReminder(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_EXTEND -> {
                // Snooze 1 hour; allow another notification after snooze.
                ReminderPreferences.clearEndOfDayFired(context)
                val until = System.currentTimeMillis() + EXTEND_MILLIS
                ReminderPreferences.setEndOfDaySnoozeUntilMillis(context, until)
                ReminderScheduler.scheduleEndOfDaySnooze(context, until)
                pendingResult.finish()
            }
            else -> pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_CLOCK_OUT = "com.rmltd.workhourstracker.ACTION_EOD_CLOCK_OUT"
        const val ACTION_EXTEND = "com.rmltd.workhourstracker.ACTION_EOD_EXTEND"
        private const val EXTEND_MILLIS = 60L * 60L * 1000L
    }
}
