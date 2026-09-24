package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences
import com.rmltd.workhourstracker.ui.theme.AppFontStyle
import com.rmltd.workhourstracker.ui.theme.AppTheme

/**
 * Persistent storage for appearance preferences (color theme + font style).
 * Same SharedPreferences style as [ReminderPreferences].
 * Defaults: [AppTheme.PURPLE], [AppFontStyle.DEFAULT].
 */
object ThemePreferences {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_COLOR_THEME = "color_theme"
    private const val KEY_FONT_STYLE = "font_style"

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

    fun getFontStyle(context: Context): AppFontStyle {
        val stored = prefs(context).getString(KEY_FONT_STYLE, null)
        return AppFontStyle.fromKey(stored)
    }

    fun setFontStyle(context: Context, style: AppFontStyle) {
        prefs(context).edit()
            .putString(KEY_FONT_STYLE, style.key)
            .apply()
    }
}
