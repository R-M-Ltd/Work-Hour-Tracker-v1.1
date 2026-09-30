package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Ephemeral session pause for timer-first Home (1.3.40).
 * Does **not** write clockOut — open punch stays OPEN for EOD / shade / widget.
 * Freeze is wall-clock millis; Resume clears prefs and continues the same OPEN punch.
 */
object SessionPausePreferences {

    private const val PREFS_NAME = "session_pause_prefs"
    private const val KEY_PAUSED = "paused"
    private const val KEY_EPOCH_DAY = "pause_epoch_day"
    private const val KEY_FREEZE_MILLIS = "pause_freeze_millis"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isPaused(context: Context, todayEpochDay: Long): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PAUSED, false)) return false
        return p.getLong(KEY_EPOCH_DAY, Long.MIN_VALUE) == todayEpochDay
    }

    /** Absolute epoch millis at which elapsed display is frozen; null if not paused. */
    fun freezeEpochMillis(context: Context, todayEpochDay: Long): Long? {
        if (!isPaused(context, todayEpochDay)) return null
        val v = prefs(context).getLong(KEY_FREEZE_MILLIS, -1L)
        return v.takeIf { it >= 0L }
    }

    fun pause(context: Context, todayEpochDay: Long, freezeEpochMillis: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putBoolean(KEY_PAUSED, true)
            .putLong(KEY_EPOCH_DAY, todayEpochDay)
            .putLong(KEY_FREEZE_MILLIS, freezeEpochMillis)
            .apply()
    }

    fun resume(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** Clear when day closes, clock-in starts fresh, or day rolls. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
