package com.example.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple persistent storage for the daily reminder time.
 * Default = 6:00 PM (18:00)
 */
object ReminderPreferences {

    private const val PREFS_NAME = "reminder_prefs"
    private const val KEY_HOUR = "reminder_hour"
    private const val KEY_MINUTE = "reminder_minute"

    private const val DEFAULT_HOUR = 18
    private const val DEFAULT_MINUTE = 0

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
}
