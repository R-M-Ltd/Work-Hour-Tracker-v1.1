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
import com.rmltd.workhourstracker.receiver.EndOfDayReminderReceiver
import com.rmltd.workhourstracker.receiver.WeeklyResetReceiver
import com.rmltd.workhourstracker.util.WeekUtils
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules the recurring exact alarms the app relies on:
 *  - a daily reminder (default 6:00 PM) to log today's hours
 *  - an end-of-day reminder (default 8:00 PM) when still clocked in
 *  - the week-start day at 2:00 AM weekly archive/reset
 *
 * Each alarm reschedules its own next occurrence when it fires, and
 * [com.rmltd.workhourstracker.receiver.BootReceiver] re-arms after reboot /
 * app update.
 */
object ReminderScheduler {

    private const val REQUEST_DAILY = 1001
    private const val REQUEST_WEEKLY = 1002
    private const val REQUEST_END_OF_DAY = 1003
    private const val REQUEST_END_OF_DAY_SNOOZE = 1004
    private const val REQUEST_END_OF_DAY_RETRY = 1005

    /** Default delay for same-day fail-closed retry after an EOD check error. */
    const val END_OF_DAY_RETRY_DELAY_MILLIS = 15L * 60L * 1000L

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
        alarmManager.cancel(pendingBroadcast(context, DailyReminderReceiver::class.java, REQUEST_DAILY))
    }

    fun scheduleEndOfDayReminder(context: Context, from: LocalDateTime = LocalDateTime.now()) {
        if (!ReminderPreferences.isEndOfDayEnabled(context)) {
            cancelEndOfDayReminder(context)
            cancelEndOfDaySnooze(context)
            cancelEndOfDaySameDayRetry(context)
            return
        }
        // If a snooze is still in the future, keep the snooze alarm instead.
        val snoozeUntil = ReminderPreferences.getEndOfDaySnoozeUntilMillis(context)
        if (snoozeUntil > System.currentTimeMillis()) {
            scheduleEndOfDaySnooze(context, snoozeUntil)
            return
        }
        ReminderPreferences.clearEndOfDaySnooze(context)
        val (hour, minute) = ReminderPreferences.getEndOfDayTime(context)
        var next = from.toLocalDate().atTime(hour, minute)
        if (!next.isAfter(from)) next = next.plusDays(1)
        val intent = Intent(context, EndOfDayReminderReceiver::class.java)
        scheduleIntent(context, next, intent, REQUEST_END_OF_DAY)
    }

    fun scheduleEndOfDaySnooze(context: Context, untilMillis: Long) {
        if (!ReminderPreferences.isEndOfDayEnabled(context)) {
            cancelEndOfDaySnooze(context)
            return
        }
        val intent = Intent(context, EndOfDayReminderReceiver::class.java).apply {
            putExtra(EndOfDayReminderReceiver.EXTRA_SNOOZE_FIRE, true)
        }
        val at = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(untilMillis),
            ZoneId.systemDefault()
        )
        scheduleIntent(context, at, intent, REQUEST_END_OF_DAY_SNOOZE)
    }

    fun cancelEndOfDayReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(
            pendingBroadcast(context, EndOfDayReminderReceiver::class.java, REQUEST_END_OF_DAY)
        )
    }

    fun cancelEndOfDaySnooze(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EndOfDayReminderReceiver::class.java).apply {
            putExtra(EndOfDayReminderReceiver.EXTRA_SNOOZE_FIRE, true)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_END_OF_DAY_SNOOZE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    /**
     * Short same-day one-shot after a fail-closed EOD check error.
     * Does not mark fired; receiver keeps fail-closed on notify.
     * No-ops if the retry would land after local midnight (next cutoff covers it).
     */
    fun scheduleEndOfDaySameDayRetry(
        context: Context,
        delayMillis: Long = END_OF_DAY_RETRY_DELAY_MILLIS
    ) {
        if (!ReminderPreferences.isEndOfDayEnabled(context)) {
            cancelEndOfDaySameDayRetry(context)
            return
        }
        val until = System.currentTimeMillis() + delayMillis
        val at = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(until),
            ZoneId.systemDefault()
        )
        if (at.toLocalDate() != LocalDateTime.now().toLocalDate()) {
            cancelEndOfDaySameDayRetry(context)
            return
        }
        val intent = Intent(context, EndOfDayReminderReceiver::class.java).apply {
            putExtra(EndOfDayReminderReceiver.EXTRA_RETRY_FIRE, true)
        }
        scheduleIntent(context, at, intent, REQUEST_END_OF_DAY_RETRY)
    }

    fun cancelEndOfDaySameDayRetry(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, EndOfDayReminderReceiver::class.java).apply {
            putExtra(EndOfDayReminderReceiver.EXTRA_RETRY_FIRE, true)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_END_OF_DAY_RETRY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
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

    private fun pendingBroadcast(
        context: Context,
        receiver: Class<*>,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, receiver)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedule(context: Context, at: LocalDateTime, receiver: Class<*>, requestCode: Int) {
        val intent = Intent(context, receiver)
        scheduleIntent(context, at, intent, requestCode)
    }

    private fun scheduleIntent(
        context: Context,
        at: LocalDateTime,
        intent: Intent,
        requestCode: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = WeekUtils.epochMillis(at)
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
