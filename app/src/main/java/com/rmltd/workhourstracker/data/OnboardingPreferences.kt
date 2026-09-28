package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences

/**
 * First-run onboarding complete flag (1.3.37).
 * Same SharedPreferences style as [ThemePreferences] / [ReminderPreferences].
 * Plain prefs — not EncryptedSharedPreferences; no Room migration.
 *
 * Default: incomplete ([DEFAULT_COMPLETE] = false) → show onboarding after splash.
 * Skip and Get started both call [setComplete](true).
 */
object OnboardingPreferences {

    const val PREFS_NAME = "onboarding_prefs"
    const val KEY_COMPLETE = "prefs_onboarding_complete"
    const val DEFAULT_COMPLETE = false

    /** Nav route when onboarding is still needed. */
    const val ROUTE_ONBOARDING = "onboarding"

    /** Durable Home route (matches AppNavigation Routes.HOME). */
    const val ROUTE_HOME = "home"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_COMPLETE, DEFAULT_COMPLETE)

    fun setComplete(context: Context, value: Boolean = true) {
        prefs(context).edit()
            .putBoolean(KEY_COMPLETE, value)
            .apply()
    }

    /**
     * Pure startDestination selector for NavHost / unit tests.
     * Incomplete → onboarding (Home not composed underneath).
     * Complete → Home (existing splash → Home path).
     */
    fun startDestination(isComplete: Boolean): String =
        if (isComplete) ROUTE_HOME else ROUTE_ONBOARDING

    /**
     * Pure model of Skip / Get started: both always mark complete = true.
     * JVM unit tests cover the gate without needing a Context.
     */
    fun markComplete(): Boolean = true
}
