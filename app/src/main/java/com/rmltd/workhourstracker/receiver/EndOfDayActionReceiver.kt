package com.rmltd.workhourstracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.rmltd.workhourstracker.MainActivity
import com.rmltd.workhourstracker.R
import com.rmltd.workhourstracker.WorkHoursApplication
import com.rmltd.workhourstracker.data.ClockOutResult
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
 * Clock-out failures are toasted and re-notified — never silently treated as success.
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
                        if (repo == null) {
                            notifyClockOutFailed(context, "Could not clock out — open the app")
                            return@launch
                        }
                        val now = LocalTime.now()
                        val minutes = now.hour * 60 + now.minute
                        val result = repo.clockOutNow(LocalDate.now(), minutes)
                        when (result) {
                            ClockOutResult.SUCCESS, ClockOutResult.SUCCESS_OVERNIGHT -> {
                                WorkHoursWidgetUpdater.requestUpdate(context)
                                ReminderPreferences.clearEndOfDaySnooze(context)
                                ReminderScheduler.scheduleEndOfDayReminder(context)
                                toast(context, "Clocked out")
                            }
                            ClockOutResult.ALREADY_CLOSED -> {
                                WorkHoursWidgetUpdater.requestUpdate(context)
                                ReminderPreferences.clearEndOfDaySnooze(context)
                                ReminderScheduler.scheduleEndOfDayReminder(context)
                                toast(context, "Already clocked out")
                            }
                            ClockOutResult.FAILED -> {
                                notifyClockOutFailed(
                                    context,
                                    "Could not clock out — open the app to finish"
                                )
                                toast(context, "Clock out failed — open the app")
                            }
                        }
                    } catch (_: Exception) {
                        notifyClockOutFailed(
                            context,
                            "Could not clock out — open the app to finish"
                        )
                        toast(context, "Clock out failed — open the app")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_EXTEND -> {
                // Snooze 1 hour; allow another notification after snooze.
                // Cancel any fail-closed same-day retry so it cannot race the snooze notify.
                ReminderPreferences.clearEndOfDayFired(context)
                ReminderScheduler.cancelEndOfDaySameDayRetry(context)
                val until = System.currentTimeMillis() + EXTEND_MILLIS
                ReminderPreferences.setEndOfDaySnoozeUntilMillis(context, until)
                ReminderScheduler.scheduleEndOfDaySnooze(context, until)
                pendingResult.finish()
            }
            else -> pendingResult.finish()
        }
    }

    private fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun notifyClockOutFailed(context: Context, body: String) {
        val channelId = EndOfDayReminderReceiver.CHANNEL_ID
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
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val clockOutIntent = Intent(context, EndOfDayActionReceiver::class.java).apply {
            action = ACTION_CLOCK_OUT
        }
        val clockOutPending = PendingIntent.getBroadcast(
            context, 1, clockOutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Still clocked in")
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, "Clock out", clockOutPending)
            .build()
        manager.notify(EndOfDayReminderReceiver.NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION_CLOCK_OUT = "com.rmltd.workhourstracker.ACTION_EOD_CLOCK_OUT"
        const val ACTION_EXTEND = "com.rmltd.workhourstracker.ACTION_EOD_EXTEND"
        private const val EXTEND_MILLIS = 60L * 60L * 1000L
    }
}
