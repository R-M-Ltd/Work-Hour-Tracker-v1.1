package com.rmltd.workhourstracker.ui.navigation

/**
 * Deep-link targets for More → Settings section scroll (1.3.41).
 * Null / none = Settings root (no auto-scroll).
 */
enum class SettingsSection {
    /** "Export / share" */
    EXPORT,
    /** "Weekly goal" (+ Hourly rate cluster) */
    RATES_GOALS,
    /** "Appearance" */
    APPEARANCE,
    /** "Backup & restore" (+ Cloud sync) */
    BACKUP_CLOUD,
    /** "Daily reminder" (+ Notification clock controls + End-of-day reminder) */
    REMINDERS_SHADE;

    companion object {
        fun fromNavArg(raw: String?): SettingsSection? {
            if (raw.isNullOrBlank() || raw.equals("none", ignoreCase = true)) return null
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        }
    }
}
