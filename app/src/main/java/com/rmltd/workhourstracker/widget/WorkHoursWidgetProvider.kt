package com.rmltd.workhourstracker.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home-screen widget: today's clock status + week hours vs goal.
 * Tap opens the app (Home). No clock-in/out actions (overnight dialogs /
 * ViewModel single-flight stay in-app).
 *
 * Refresh: system [onUpdate] (~30 min via updatePeriodMillis), plus explicit
 * [WorkHoursWidgetUpdater.requestUpdate] after clock changes and app resume.
 */
class WorkHoursWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WorkHoursWidgetUpdater.updateAppWidgetIds(context, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        WorkHoursWidgetUpdater.requestUpdate(context)
    }
}
