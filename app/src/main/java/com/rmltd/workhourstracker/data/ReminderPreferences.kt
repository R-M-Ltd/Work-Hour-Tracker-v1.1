package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Persistent storage for reminder, week-start, and weekly-goal preferences.
 * Defaults: reminder 6:00 PM enabled; week starts Wednesday; goal 40.00 hours;
 * end-of-day cutoff 8:00 PM enabled (fires only while still clocked in).
 */
object ReminderPreferences {

    private const val PREFS_NAME = "reminder_prefs"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"
    private const val KEY_ENABLED = "reminder_enabled"
    private const val KEY_WEEK_START = "week_start_day"
    private const val KEY_WEEKLY_GOAL = "weekly_goal_hours"
    private const val KEY_EOD_HOUR = "end_of_day_hour"
    private const val KEY_EOD_MINUTE = "end_of_day_minute"
    private const val KEY_EOD_ENABLED = "end_of_day_enabled"
    private const val KEY_EOD_FIRED_EPOCH = "end_of_day_fired_epoch_day"
    private const val KEY_EOD_SNOOZE_UNTIL = "end_of_day_snooze_until_millis"

    private const val DEFAULT_HOUR = 18
    private const val DEFAULT_MINUTE = 0
    private const val DEFAULT_ENABLED = true
    /** java.time DayOfWeek value: Wednesday = 3 */
    private const val DEFAULT_WEEK_START = 3
    private const val DEFAULT_WEEKLY_GOAL = 40.0
    private const val DEFAULT_EOD_HOUR = 20
    private const val DEFAULT_EOD_MINUTE = 0
    private const val DEFAULT_EOD_ENABLED = true

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getReminderTime(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        val hour = p.getInt(KEY_HOUR, DEFAULT_HOUR)
        val minute = p.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        return hour to minute
    }

    fun setReminderTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    fun isReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getWeekStartDay(context: Context): DayOfWeek {
        val value = prefs(context).getInt(KEY_WEEK_START, DEFAULT_WEEK_START)
        return DayOfWeek.of(value.coerceIn(1, 7))
    }

    fun setWeekStartDay(context: Context, day: DayOfWeek) {
        prefs(context).edit().putInt(KEY_WEEK_START, day.value).apply()
    }

    fun getWeeklyGoalHours(context: Context): Double {
        val stored = prefs(context).getString(KEY_WEEKLY_GOAL, null)
        return stored?.toDoubleOrNull()?.coerceAtLeast(0.01) ?: DEFAULT_WEEKLY_GOAL
    }

    fun setWeeklyGoalHours(context: Context, hours: Double) {
        val safe = hours.coerceIn(0.01, 168.0)
        prefs(context).edit()
            .putString(KEY_WEEKLY_GOAL, "%.2f".format(java.util.Locale.US, safe))
            .apply()
    }

    // --- End-of-day (still clocked in) reminder ---

    fun getEndOfDayTime(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        return p.getInt(KEY_EOD_HOUR, DEFAULT_EOD_HOUR) to
            p.getInt(KEY_EOD_MINUTE, DEFAULT_EOD_MINUTE)
    }

    fun setEndOfDayTime(context: Context, hour: Int, minute: Int) {
        prefs(context).edit()
            .putInt(KEY_EOD_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_EOD_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    fun isEndOfDayEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_EOD_ENABLED, DEFAULT_EOD_ENABLED)

    fun setEndOfDayEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EOD_ENABLED, enabled).apply()
    }

    /** Epoch day of last end-of-day notification (one per calendar day unless snoozed). */
    fun getEndOfDayFiredEpochDay(context: Context): Long =
        prefs(context).getLong(KEY_EOD_FIRED_EPOCH, Long.MIN_VALUE)

    fun markEndOfDayFired(context: Context, epochDay: Long = LocalDate.now().toEpochDay()) {
        prefs(context).edit().putLong(KEY_EOD_FIRED_EPOCH, epochDay).apply()
    }

    fun clearEndOfDayFired(context: Context) {
        prefs(context).edit().remove(KEY_EOD_FIRED_EPOCH).apply()
    }

    fun getEndOfDaySnoozeUntilMillis(context: Context): Long =
        prefs(context).getLong(KEY_EOD_SNOOZE_UNTIL, 0L)

    fun setEndOfDaySnoozeUntilMillis(context: Context, untilMillis: Long) {
        prefs(context).edit().putLong(KEY_EOD_SNOOZE_UNTIL, untilMillis).apply()
    }

    fun clearEndOfDaySnooze(context: Context) {
        prefs(context).edit().remove(KEY_EOD_SNOOZE_UNTIL).apply()
    }
}
