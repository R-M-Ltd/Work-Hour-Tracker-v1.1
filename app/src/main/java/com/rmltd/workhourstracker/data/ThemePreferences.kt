package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import com.rmltd.workhourstracker.ui.theme.AppFontStyle
import com.rmltd.workhourstracker.ui.theme.AppTheme

/**
 * Persistent storage for appearance preferences (color theme + font style + light/dark).
 * Same SharedPreferences style as [ReminderPreferences].
 * Defaults: [AppTheme.PURPLE], [AppFontStyle.DEFAULT], [AppearanceMode.SYSTEM].
 */
object ThemePreferences {

    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_COLOR_THEME = "color_theme"
    private const val KEY_FONT_STYLE = "font_style"
    private const val KEY_APPEARANCE_MODE = "appearance_mode"

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

    fun getAppearanceMode(context: Context): AppearanceMode {
        val stored = prefs(context).getString(KEY_APPEARANCE_MODE, null)
        return AppearanceMode.fromKey(stored)
    }

    fun setAppearanceMode(context: Context, mode: AppearanceMode) {
        prefs(context).edit()
            .putString(KEY_APPEARANCE_MODE, mode.key)
            .apply()
    }

    /** Resolved dark flag for Compose / widget chrome (Appearance then system). */
    fun resolveDark(context: Context): Boolean {
        val systemDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return getAppearanceMode(context).resolveDark(systemDark)
    }
}
