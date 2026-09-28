package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Settings → Notification clock controls (shade session notification).
 * Default **off**. Channel id is owned by [com.rmltd.workhourstracker.receiver.ClockSessionNotifier].
 */
object ShadePreferences {

    private const val PREFS_NAME = "shade_prefs"
    private const val KEY_ENABLED = "notification_clock_controls"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isClockControlsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setClockControlsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
