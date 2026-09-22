package com.rmltd.workhourstracker.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.receiver.DailyReminderReceiver
import com.rmltd.workhourstracker.receiver.WeeklyResetReceiver
import com.rmltd.workhourstracker.util.WeekUtils
import java.time.LocalDateTime

/**
 * Schedules the two recurring exact alarms the app relies on:
 *  - a daily reminder (default 6:00 PM, from [ReminderPreferences]) to log today's hours
 *  - the week-start day at 2:00 AM weekly archive/reset
 *
 * Each alarm reschedules its own next occurrence when it fires (see the
 * receivers in this package), and [com.rmltd.workhourstracker.receiver.BootReceiver]
 * re-arms both after a device reboot or app update (`MY_PACKAGE_REPLACED`),
 * since exact alarms do not survive those.
 */
object ReminderScheduler {

    private const val REQUEST_DAILY = 1001
    private const val REQUEST_WEEKLY = 1002

    fun scheduleDailyReminder(context: Context, from: LocalDateTime = LocalDateTime.now()) {
        if (!ReminderPreferences.isReminderEnabled(context)) {
            cancelDailyReminder(context)
            return
        }
        val (hour, minute) = ReminderPreferences.getReminderTime(context)
        var next = from.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(from)) next = next.plusDays(1)
        schedule(context, next, DailyReminderReceiver::class.java, REQUEST_DAILY)
    }

    fun cancelDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(dailyPendingIntent(context))
    }

    fun scheduleWeeklyReset(context: Context, from: LocalDateTime = LocalDateTime.now()) {
        val weekStartDay = ReminderPreferences.getWeekStartDay(context)
        val next = WeekUtils.nextWeekStart2AM(from, weekStartDay)
        schedule(context, next, WeeklyResetReceiver::class.java, REQUEST_WEEKLY)
    }


    /** True when exact alarms are allowed (always on API < 31). */
    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    /**
     * Opens the system screen to grant [Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM]
     * for this app (API 31+). No-op on older APIs.
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** True when the user has allowed this app to post notifications. */
    fun areNotificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Opens the system notification settings for this app so the user can
     * re-enable POST_NOTIFICATIONS / channel alerts when denied.
     */
    fun openAppNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun dailyPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, DailyReminderReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_DAILY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedule(context: Context, at: LocalDateTime, receiver: Class<*>, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, receiver)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = WeekUtils.epochMillis(at)
        // Android 12+ can deny exact alarms; setExactAndAllowWhileIdle then throws
        // SecurityException and would crash Application.onCreate if uncaught.
        try {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }
}
