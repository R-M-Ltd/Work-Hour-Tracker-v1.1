package com.rmltd.workhourstracker.data

/**
 * App-wide light/dark preference (Settings → Appearance).
 * Default [SYSTEM] preserves pre-1.3.38 behavior when the pref is missing.
 */
enum class AppearanceMode(val key: String, val displayName: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    /** Resolve to whether UI/widget should use the dark palette. */
    fun resolveDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        val DEFAULT: AppearanceMode = SYSTEM

        fun fromKey(raw: String?): AppearanceMode =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: DEFAULT
    }
}
