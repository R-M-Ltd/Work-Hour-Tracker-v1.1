package com.rmltd.workhourstracker.ui.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * User-selectable in-app font families for [WorkHoursTheme] typography.
 *
 * Uses platform [FontFamily] constants (no bundled font files).
 * Stable [key] values are persisted in SharedPreferences; [displayName] is Settings UI copy.
 * Default is [DEFAULT] (Material / system default look).
 * Unknown backup/pref keys fall back to [DEFAULT] via [fromKey].
 *
 * Options map 1:1 to Compose platform generics (Default, SansSerif, Serif, Monospace, Cursive).
 * No invented / fake font labels — those five are the available constants.
 */
enum class AppFontStyle(val key: String, val displayName: String) {
    DEFAULT("default", "Default"),
    SANS_SERIF("sans_serif", "Sans Serif"),
    SERIF("serif", "Serif"),
    MONOSPACE("monospace", "Monospace"),
    CURSIVE("cursive", "Cursive");

    fun toFontFamily(): FontFamily = when (this) {
        DEFAULT -> FontFamily.Default
        SANS_SERIF -> FontFamily.SansSerif
        SERIF -> FontFamily.Serif
        MONOSPACE -> FontFamily.Monospace
        CURSIVE -> FontFamily.Cursive
    }

    companion object {
        val DEFAULT_STYLE: AppFontStyle = DEFAULT

        fun fromKey(key: String?): AppFontStyle =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT_STYLE
    }
}
