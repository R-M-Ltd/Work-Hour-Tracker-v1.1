package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Local-only timestamps for last successful backup / restore.
 * Not written into backup JSON schema v1. Distinct from cloud last-synced.
 */
object BackupPreferences {

    private const val PREFS_NAME = "backup_prefs"
    private const val KEY_LAST_BACKUP = "last_backup_epoch_millis"
    private const val KEY_LAST_RESTORE = "last_restore_epoch_millis"

    /** Stale nudge threshold (spec: 7 days). */
    const val STALE_AFTER_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastBackupEpochMillis(context: Context): Long =
        prefs(context).getLong(KEY_LAST_BACKUP, 0L)

    fun setLastBackupEpochMillis(context: Context, epochMillis: Long) {
        prefs(context).edit().putLong(KEY_LAST_BACKUP, epochMillis).apply()
    }

    fun getLastRestoreEpochMillis(context: Context): Long =
        prefs(context).getLong(KEY_LAST_RESTORE, 0L)

    fun setLastRestoreEpochMillis(context: Context, epochMillis: Long) {
        prefs(context).edit().putLong(KEY_LAST_RESTORE, epochMillis).apply()
    }

    /** Never backed up, or last backup older than [STALE_AFTER_MILLIS]. */
    fun isBackupStale(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean =
        isStale(getLastBackupEpochMillis(context), nowMillis)

    /** Pure stale check (unit-testable). last<=0 means Never. */
    fun isStale(lastBackupEpochMillis: Long, nowMillis: Long): Boolean {
        if (lastBackupEpochMillis <= 0L) return true
        return nowMillis - lastBackupEpochMillis > STALE_AFTER_MILLIS
    }
}
