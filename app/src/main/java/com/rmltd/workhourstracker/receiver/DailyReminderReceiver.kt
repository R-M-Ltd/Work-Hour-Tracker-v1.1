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
 * Daily reminder. Skips the notification when today already has a complete
 * clock-out (smarter reminder). Always re-arms the next day's alarm.
 */
class DailyReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        // Re-arm tomorrow's reminder — exact alarms are one-shot.
        ReminderScheduler.scheduleDailyReminder(context)

        if (!ReminderPreferences.isReminderEnabled(context)) {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? WorkHoursApplication
                val todayEntry = app?.repository?.entryForDateOnce(LocalDate.now())
                val alreadyComplete = todayEntry?.clockOutMinutes != null
                if (!alreadyComplete) {
                    showNotification(context)
                }
            } catch (_: Exception) {
                // If DB check fails, still remind — better noisy than silent.
                showNotification(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context) {
        val channelId = "daily_reminder"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Daily hours reminder", NotificationManager.IMPORTANCE_DEFAULT)
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
            .setContentTitle("Log today's hours")
            .setContentText("Tap to clock in/out the hours you worked today.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
    }
}
