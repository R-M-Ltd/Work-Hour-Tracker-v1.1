package com.rmltd.workhourstracker.ui.theme

/**
 * User-selectable color palettes for [WorkHoursTheme].
 *
 * Stable [key] values are persisted in SharedPreferences; [displayName] is Settings UI copy.
 * Default is [PURPLE] (current polished Home-matching palette).
 *
 * ColorScheme hexes for Purple/Blue/Red/Green/Orange are design-locked in
 * theme-mockups/palettes.json (approved as-is). Aqua is an additional shipped palette
 * (not in the locked pack) with a documented teal-aqua primary set.
 * Ed owns preference keys/wiring; Ivan may still polish the Settings theme *picker* UI only.
 */
enum class AppTheme(val key: String, val displayName: String) {
    PURPLE("purple", "Purple"),
    BLUE("blue", "Blue"),
    RED("red", "Red"),
    GREEN("green", "Green"),
    ORANGE("orange", "Orange"),
    AQUA("aqua", "Aqua");

    companion object {
        val DEFAULT: AppTheme = PURPLE

        fun fromKey(key: String?): AppTheme =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DEFAULT
    }
}
