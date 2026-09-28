package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences
import com.rmltd.workhourstracker.util.CommentsOnlyReconcileDecision
import com.rmltd.workhourstracker.util.HomeDraftSnapshot
import com.rmltd.workhourstracker.util.HomeDraftStash

/**
 * Durable Home draft stash (S-B) — survives process death.
 * Keyed by [HomeDraftSnapshot.epochDay] (`todayEpochDay`).
 * Same SharedPreferences style as [ThemePreferences] / [ReminderPreferences].
 */
object HomeDraftPreferences {

    private const val PREFS_NAME = "home_draft_prefs"
    private const val KEY_EPOCH_DAY = "draft_epoch_day"
    private const val KEY_IN_MINUTES = "draft_in_minutes"
    private const val KEY_OUT_MINUTES = "draft_out_minutes"
    private const val KEY_COMMENTS = "draft_comments"
    private const val KEY_HAS_IN = "draft_has_in"
    private const val KEY_HAS_OUT = "draft_has_out"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): HomeDraftSnapshot? {
        val p = prefs(context)
        if (!p.contains(KEY_EPOCH_DAY)) return null
        val epoch = p.getLong(KEY_EPOCH_DAY, Long.MIN_VALUE)
        val inMinutes =
            if (p.getBoolean(KEY_HAS_IN, false)) p.getInt(KEY_IN_MINUTES, 0) else null
        val outMinutes =
            if (p.getBoolean(KEY_HAS_OUT, false)) p.getInt(KEY_OUT_MINUTES, 0) else null
        val comments = p.getString(KEY_COMMENTS, "") ?: ""
        return HomeDraftSnapshot(
            epochDay = epoch,
            inMinutes = inMinutes,
            outMinutes = outMinutes,
            comments = comments
        )
    }

    fun save(context: Context, snapshot: HomeDraftSnapshot) {
        val editor = prefs(context).edit()
            .putLong(KEY_EPOCH_DAY, snapshot.epochDay)
            .putBoolean(KEY_HAS_IN, snapshot.inMinutes != null)
            .putBoolean(KEY_HAS_OUT, snapshot.outMinutes != null)
            .putString(KEY_COMMENTS, snapshot.comments)
        if (snapshot.inMinutes != null) {
            editor.putInt(KEY_IN_MINUTES, snapshot.inMinutes)
        } else {
            editor.remove(KEY_IN_MINUTES)
        }
        if (snapshot.outMinutes != null) {
            editor.putInt(KEY_OUT_MINUTES, snapshot.outMinutes)
        } else {
            editor.remove(KEY_OUT_MINUTES)
        }
        editor.apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** Clear only when stored epoch matches [todayEpochDay] (successful Save / clean). */
    fun clearIfEpochDay(context: Context, todayEpochDay: Long) {
        val stash = load(context) ?: return
        if (stash.epochDay == todayEpochDay) clear(context)
    }

    /**
     * S1: comments-only History/Log save — preserve dirty IN/OUT; clear when clocks clean.
     * [roomClockIn]/[roomClockOut] are Room minutes for [epochDay] (post-write clocks unchanged).
     */
    fun reconcileCommentsOnly(
        context: Context,
        epochDay: Long,
        newComments: String,
        roomClockIn: Int?,
        roomClockOut: Int?
    ) {
        when (
            val decision = HomeDraftStash.decideCommentsOnlyReconcile(
                stash = load(context),
                epochDay = epochDay,
                newComments = newComments,
                roomIn = roomClockIn,
                roomOut = roomClockOut
            )
        ) {
            CommentsOnlyReconcileDecision.NoOp -> Unit
            CommentsOnlyReconcileDecision.Clear -> clearIfEpochDay(context, epochDay)
            is CommentsOnlyReconcileDecision.KeepClocksUpdateComments -> save(context, decision.snapshot)
        }
    }
}
