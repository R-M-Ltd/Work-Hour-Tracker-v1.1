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
import com.rmltd.workhourstracker.data.ClockDayState
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.util.EndOfDayCompleteness
import com.rmltd.workhourstracker.worker.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * End-of-day reminder polish (1.3.38): empty → gentle wrap-up; incomplete →
 * still clocked in; complete → skip. Actions: Open app + Dismiss only —
 * **no Clock out** (shade owns punch actions on channel clock_session).
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
                if (alreadyFiredToday && !isSnooze && !isRetry) {
                    ReminderScheduler.scheduleEndOfDayReminder(context)
                    return@launch
                }

                val app = context.applicationContext as? WorkHoursApplication
                val repo = app?.repository
                val todayEntry = runCatching { repo?.entryForDateOnce(LocalDate.now()) }.getOrElse {
                    throw it
                }
                val openEntry = runCatching { repo?.findOpenEntryOnce() }.getOrElse { throw it }
                val overnightOrOrphan = openEntry != null &&
                    openEntry.dateEpochDay != today &&
                    ClockDayState.isOvernightOpen(openEntry.clockInMinutes, openEntry.clockOutMinutes)

                val state = EndOfDayCompleteness.classify(todayEntry, overnightOrOrphan)
                val copy = EndOfDayCompleteness.notificationCopy(state)
                if (copy != null) {
                    ReminderPreferences.markEndOfDayFired(context, today)
                    ReminderPreferences.clearEndOfDaySnooze(context)
                    ReminderScheduler.cancelEndOfDaySameDayRetry(context)
                    showNotification(context, copy.title, copy.body)
                } else {
                    // Complete day — skip notify; still mark fired so we do not re-ping.
                    ReminderPreferences.markEndOfDayFired(context, today)
                    ReminderPreferences.clearEndOfDaySnooze(context)
                    ReminderScheduler.cancelEndOfDaySameDayRetry(context)
                }

                if (!isSnooze && !isRetry) {
                    ReminderScheduler.scheduleEndOfDayReminder(context)
                }
            } catch (_: Exception) {
                ReminderScheduler.scheduleEndOfDaySameDayRetry(context)
                if (!isSnooze && !isRetry) {
                    ReminderScheduler.scheduleEndOfDayReminder(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, title: String, body: String) {
        val channelId = CHANNEL_ID
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "End-of-day reminder",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Gentle wrap-up when today is empty or unfinished"
                }
            )
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            // Open app only — no Clock out / Extend (shade owns punch actions).
            .addAction(0, "Open app", contentIntent)
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
