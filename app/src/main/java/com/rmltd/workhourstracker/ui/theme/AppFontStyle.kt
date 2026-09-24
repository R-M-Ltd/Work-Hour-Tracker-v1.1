package com.rmltd.workhourstracker.ui.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * User-selectable in-app font families for [WorkHoursTheme] typography.
 *
 * Uses platform [FontFamily] constants (no bundled font files).
 * Stable [key] values are persisted in SharedPreferences; [displayName] is Settings UI copy.
 * Default is [DEFAULT] (Material / system default look).
 *
 * Options:
 * - Default — Material default / system UI font
 * - Sans Serif — [FontFamily.SansSerif]
 * - Serif — [FontFamily.Serif]
 * - Monospace — [FontFamily.Monospace]
 */
enum class AppFontStyle(val key: String, val displayName: String) {
    DEFAULT("default", "Default"),
    SANS_SERIF("sans_serif", "Sans Serif"),
    SERIF("serif", "Serif"),
    MONOSPACE("monospace", "Monospace");

    fun toFontFamily(): FontFamily = when (this) {
        DEFAULT -> FontFamily.Default
        SANS_SERIF -> FontFamily.SansSerif
        SERIF -> FontFamily.Serif
        MONOSPACE -> FontFamily.Monospace
    }

    companion object {
        val DEFAULT_STYLE: AppFontStyle = DEFAULT

        fun fromKey(key: String?): AppFontStyle =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT_STYLE
    }
}
