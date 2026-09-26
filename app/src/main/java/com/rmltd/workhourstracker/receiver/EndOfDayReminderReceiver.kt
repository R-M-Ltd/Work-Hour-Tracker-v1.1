package com.rmltd.workhourstracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rmltd.workhourstracker.MainActivity
import com.rmltd.workhourstracker.R
import com.rmltd.workhourstracker.WorkHoursApplication
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.worker.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * End-of-day reminder: fires once when still clocked in past the user cutoff.
 * Actions: Clock out (now) or Extend (snooze 1 hour). Always re-arms the next
 * scheduled cutoff (or snooze one-shot). On check failure: fail-closed on notify,
 * schedule a short same-day retry without marking fired.
 */
class EndOfDayReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val isSnooze = intent.getBooleanExtra(EXTRA_SNOOZE_FIRE, false)
        val isRetry = intent.getBooleanExtra(EXTRA_RETRY_FIRE, false)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!ReminderPreferences.isEndOfDayEnabled(context)) {
                    ReminderScheduler.cancelEndOfDayReminder(context)
                    ReminderScheduler.cancelEndOfDaySameDayRetry(context)
                    return@launch
                }

                val today = LocalDate.now().toEpochDay()
                val alreadyFiredToday =
                    ReminderPreferences.getEndOfDayFiredEpochDay(context) == today
                // Snooze / same-day retry may notify again; regular cutoff is once per day.
                if (alreadyFiredToday && !isSnooze && !isRetry) {
                    ReminderScheduler.scheduleEndOfDayReminder(context)
                    return@launch
                }

                val app = context.applicationContext as? WorkHoursApplication
                val open = runCatching {
                    app?.repository?.isStillClockedIn() == true
                }.getOrElse {
                    // Treat repository failure like outer catch: fail closed + retry.
                    throw it
                }

                if (open) {
                    ReminderPreferences.markEndOfDayFired(context, today)
                    ReminderPreferences.clearEndOfDaySnooze(context)
                    ReminderScheduler.cancelEndOfDaySameDayRetry(context)
                    showNotification(context)
                }

                // Re-arm next cutoff (snooze path schedules its own one-shot separately).
                if (!isSnooze && !isRetry) {
                    ReminderScheduler.scheduleEndOfDayReminder(context)
                }
            } catch (_: Exception) {
                // Fail closed on notify: never show "Still clocked in" when open
                // state is unknown. Do NOT mark fired. Schedule a short same-day
                // one-shot retry, and re-arm the next cutoff for the regular path.
                ReminderScheduler.scheduleEndOfDaySameDayRetry(context)
                if (!isSnooze && !isRetry) {
                    ReminderScheduler.scheduleEndOfDayReminder(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context) {
        val channelId = CHANNEL_ID
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "End-of-day clock-out reminder",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockOutIntent = Intent(context, EndOfDayActionReceiver::class.java).apply {
            action = EndOfDayActionReceiver.ACTION_CLOCK_OUT
        }
        val clockOutPending = PendingIntent.getBroadcast(
            context, 1, clockOutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val extendIntent = Intent(context, EndOfDayActionReceiver::class.java).apply {
            action = EndOfDayActionReceiver.ACTION_EXTEND
        }
        val extendPending = PendingIntent.getBroadcast(
            context, 2, extendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Still clocked in")
            .setContentText("Past your end-of-day time. Clock out now, or extend for 1 hour.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, "Clock out", clockOutPending)
            .addAction(0, "Extend 1h", extendPending)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "end_of_day_reminder"
        const val NOTIFICATION_ID = 2002
        const val EXTRA_SNOOZE_FIRE = "snooze_fire"
        const val EXTRA_RETRY_FIRE = "retry_fire"
    }
}
