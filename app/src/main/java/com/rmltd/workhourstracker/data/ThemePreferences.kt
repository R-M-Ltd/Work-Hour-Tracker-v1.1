package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences
import com.rmltd.workhourstracker.ui.theme.AppTheme

/**
 * Persistent storage for the user-selected color theme.
 * Same SharedPreferences style as [ReminderPreferences].
 * Default: [AppTheme.PURPLE].
 */
object ThemePreferences {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_COLOR_THEME = "color_theme"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getColorTheme(context: Context): AppTheme {
        val stored = prefs(context).getString(KEY_COLOR_THEME, null)
        return AppTheme.fromKey(stored)
    }

    fun setColorTheme(context: Context, theme: AppTheme) {
        prefs(context).edit()
            .putString(KEY_COLOR_THEME, theme.key)
            .apply()
    }
}
