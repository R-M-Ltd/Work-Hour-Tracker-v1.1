package com.rmltd.workhourstracker.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Light confirm haptic for successful clock punches only (1.3.25).
 * Does not fire on failures, blocked overnight dialog open, or nav-only actions.
 */
object ClockHaptics {

    fun performSuccess(view: View) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(type)
    }
}
