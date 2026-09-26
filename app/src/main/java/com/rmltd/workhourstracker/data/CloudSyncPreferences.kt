package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Opt-in cloud sync UI prefs (1.3.25). Default **off**, vendor-neutral.
 * No OAuth / automatic upload until a provider is linked in a later release.
 */
object CloudSyncPreferences {

    private const val PREFS_NAME = "cloud_sync_prefs"
    private const val KEY_ENABLED = "cloud_sync_enabled"
    private const val KEY_LINKED = "cloud_sync_linked"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Default false — hours stay on-device until the user opts in. */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Placeholder link state; remains false until a future provider ships. */
    fun isLinked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LINKED, false)

    fun setLinked(context: Context, linked: Boolean) {
        prefs(context).edit().putBoolean(KEY_LINKED, linked).apply()
    }
}
