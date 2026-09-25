package com.rmltd.workhourstracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.rmltd.workhourstracker.MainActivity
import com.rmltd.workhourstracker.R
import com.rmltd.workhourstracker.WorkHoursApplication
import com.rmltd.workhourstracker.data.ClockDayState
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.util.WeekUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Builds RemoteViews from Room + prefs and pushes them to all widget instances.
 * Display-only (tap opens [MainActivity] / Home) — no clock actions from the widget.
 *
 * [requestUpdate] is single-flight + generation-gated: overlapping launches cannot
 * apply a stale Room snapshot after a newer refresh has started.
 */
object WorkHoursWidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateMutex = Mutex()

    /** Visible for JVM unit tests of generation coalescing. */
    internal val generation = WidgetUpdateGeneration()

    /** Fire-and-forget refresh of every placed widget instance. */
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        val token = generation.nextToken()
        scope.launch {
            updateMutex.withLock {
                if (!generation.isCurrent(token)) return@withLock
                runCatching { updateAllSync(appContext) }
            }
        }
    }

    /** Blocking update for [AppWidgetProvider.onUpdate] (caller holds goAsync). */
    suspend fun updateAllSync(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, WorkHoursWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return
        val views = buildRemoteViews(context)
        ids.forEach { id -> manager.updateAppWidget(id, views) }
    }

    suspend fun updateAppWidgetIds(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val manager = AppWidgetManager.getInstance(context)
        val views = buildRemoteViews(context)
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, views) }
    }

    private suspend fun buildRemoteViews(context: Context): RemoteViews {
        val display = loadDisplay(context)
        val views = RemoteViews(context.packageName, R.layout.widget_work_hours)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
        views.setTextViewText(R.id.widget_status, display.statusLine)
        views.setTextViewText(R.id.widget_week, display.weekLine)

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        return views
    }

    private suspend fun loadDisplay(context: Context): WidgetContent.Display {
        val app = context.applicationContext as? WorkHoursApplication
        val repo = app?.repository
        val today = LocalDate.now()
        val todayEntry = repo?.entryForDateOnce(today)
        val yesterdayEntry = repo?.entryForDateOnce(today.minusDays(1))
        val overnight = ClockDayState.isOvernightOpen(
            yesterdayEntry?.clockInMinutes,
            yesterdayEntry?.clockOutMinutes
        )
        val weekStartDay = ReminderPreferences.getWeekStartDay(context)
        val weekStart = WeekUtils.weekStartFor(today, weekStartDay)
        val weekEntries = repo?.entriesForWeekOnce(weekStart).orEmpty()
        val weekHours = weekEntries.sumOf { it.hoursWorked }
        val goal = ReminderPreferences.getWeeklyGoalHours(context)
        return WidgetContent.build(
            todayIn = todayEntry?.clockInMinutes,
            todayOut = todayEntry?.clockOutMinutes,
            todayHoursWorked = todayEntry?.hoursWorked ?: 0.0,
            overnightPending = overnight,
            weekHours = weekHours,
            weekGoalHours = goal
        )
    }
}
