package com.rmltd.workhourstracker.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * ~60s open-session widget tick. Re-arms only while [WorkHoursWidgetUpdater]
 * still sees an open punch and placed widgets.
 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_OPEN_SESSION_TICK) return
        WorkHoursWidgetUpdater.requestUpdate(context)
    }

    companion object {
        const val ACTION_OPEN_SESSION_TICK =
            "com.rmltd.workhourstracker.ACTION_WIDGET_OPEN_SESSION_TICK"
    }
}
