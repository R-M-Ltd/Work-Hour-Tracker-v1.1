package com.rmltd.workhourstracker.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.rmltd.workhourstracker.MainActivity
import com.rmltd.workhourstracker.R
import com.rmltd.workhourstracker.WorkHoursApplication
import com.rmltd.workhourstracker.data.ClockDayState
import com.rmltd.workhourstracker.data.ReminderPreferences
import com.rmltd.workhourstracker.data.SessionPausePreferences
import com.rmltd.workhourstracker.data.ThemePreferences
import com.rmltd.workhourstracker.util.SessionElapsed
import com.rmltd.workhourstracker.util.SessionPause
import com.rmltd.workhourstracker.util.WeekUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime

/**
 * Builds RemoteViews from Room + prefs and pushes them to all widget instances.
 * Display-only (tap opens [MainActivity] / Home) — no clock actions from the widget.
 *
 * Chrome colors follow [ThemePreferences] Appearance-resolved light/dark + AppTheme.
 * While an open session exists and widgets are placed, schedules ~60s refreshes
 * (coalesced via [WidgetUpdateGeneration]); cancels when closed / no widgets.
 */
object WorkHoursWidgetUpdater {

    /** Widget tap → MainActivity opens Home + Edit today sheet (1.3.42). */
    const val EXTRA_OPEN_EDIT_TODAY = "com.rmltd.workhourstracker.EXTRA_OPEN_EDIT_TODAY"

    private const val OPEN_REFRESH_MS = 60_000L
    private const val REFRESH_REQUEST_CODE = 4401

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateMutex = Mutex()

    /** Visible for JVM unit tests of generation coalescing. */
    internal val generation = WidgetUpdateGeneration()

    /** Fire-and-forget refresh of every placed widget instance. */
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        val token = generation.nextToken()
        scope.launch {
            runCatching {
                updateMutex.withLock {
                    applyGated(appContext, token) { manager, ids, views ->
                        ids.forEach { id -> manager.updateAppWidget(id, views) }
                    }
                }
            }
        }
    }

    /**
     * Blocking update for [AppWidgetProvider.onUpdate] (caller holds goAsync).
     * Uses the same mutex + generation gate as [requestUpdate].
     */
    suspend fun updateAppWidgetIds(context: Context, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val token = generation.nextToken()
        updateMutex.withLock {
            applyGated(appContext, token) { manager, _, views ->
                appWidgetIds.forEach { id -> manager.updateAppWidget(id, views) }
            }
        }
    }

    /** Blocking update of every instance (same gate). Prefer [requestUpdate] from app code. */
    suspend fun updateAllSync(context: Context) {
        val appContext = context.applicationContext
        val token = generation.nextToken()
        updateMutex.withLock {
            applyGated(appContext, token) { manager, ids, views ->
                ids.forEach { id -> manager.updateAppWidget(id, views) }
            }
        }
    }

    /**
     * Inside [updateMutex]: drop if superseded before load; load Room; drop if
     * superseded after load; otherwise build RemoteViews and invoke [publish].
     */
    private suspend fun applyGated(
        context: Context,
        token: Long,
        publish: (AppWidgetManager, IntArray, RemoteViews) -> Unit
    ) {
        if (!generation.isCurrent(token)) return
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, WorkHoursWidgetProvider::class.java)
        )
        if (ids.isEmpty()) {
            cancelOpenSessionRefresh(context)
            return
        }
        val display = loadDisplay(context)
        // Critical: re-check AFTER Room load, BEFORE updateAppWidget.
        if (!generation.isCurrent(token)) return
        val views = buildRemoteViews(context, display)
        publish(manager, ids, views)
        if (display.openSession) {
            scheduleOpenSessionRefresh(context)
        } else {
            cancelOpenSessionRefresh(context)
        }
    }

    private fun buildRemoteViews(context: Context, display: WidgetContent.Display): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_work_hours)
        views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_title))
        views.setTextViewText(R.id.widget_status, display.statusLine)
        if (display.todayLine != null) {
            views.setViewVisibility(R.id.widget_today, View.VISIBLE)
            views.setTextViewText(R.id.widget_today, display.todayLine)
        } else {
            views.setViewVisibility(R.id.widget_today, View.GONE)
            views.setTextViewText(R.id.widget_today, "")
        }
        views.setTextViewText(R.id.widget_week, display.weekLine)
        applyThemeChrome(context, views, display)

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_EDIT_TODAY, true)
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

    /** Map active AppTheme + Appearance-resolved night mode onto RemoteViews chrome. */
    private fun applyThemeChrome(
        context: Context,
        views: RemoteViews,
        display: WidgetContent.Display
    ) {
        val dark = ThemePreferences.resolveDark(context)
        val theme = ThemePreferences.getColorTheme(context)
        val colors = WidgetThemeColors.resolve(theme, dark)

        views.setInt(R.id.widget_title, "setTextColor", colors.primary)
        views.setInt(R.id.widget_status, "setTextColor", colors.onSurface)
        views.setInt(R.id.widget_today, "setTextColor", colors.onSurface)
        views.setInt(R.id.widget_week, "setTextColor", colors.onSurfaceVariant)

        val density = context.resources.displayMetrics.density
        val width = (250 * density).toInt().coerceAtLeast(48)
        val height = (110 * density).toInt().coerceAtLeast(48)
        val chrome = WidgetThemeColors.buildBackgroundBitmap(
            fillColor = colors.primaryContainer,
            accentColor = colors.primary,
            widthPx = width,
            heightPx = height,
            cornerPx = 20f * density,
            accentHeightPx = 3f * density
        )
        views.setImageViewBitmap(R.id.widget_chrome, chrome)

        val todayPart = display.todayLine?.let { " $it." }.orEmpty()
        val rootCd =
            "${context.getString(R.string.widget_title)}. ${display.statusLine}.$todayPart ${display.weekLine}"
        views.setContentDescription(R.id.widget_root, rootCd)
        views.setContentDescription(R.id.widget_status, display.statusLine)
        views.setContentDescription(R.id.widget_today, display.todayLine)
        views.setContentDescription(R.id.widget_week, display.weekLine)
    }

    private suspend fun loadDisplay(context: Context): WidgetContent.Display {
        val app = context.applicationContext as? WorkHoursApplication
        val repo = app?.repository
        val today = LocalDate.now()
        val todayEntry = repo?.entryForDateOnce(today)
        val openEntry = repo?.findOpenEntryOnce()
        val overnight = openEntry != null &&
            openEntry.dateEpochDay != today.toEpochDay() &&
            ClockDayState.isOvernightOpen(openEntry.clockInMinutes, openEntry.clockOutMinutes)
        val weekStartDay = ReminderPreferences.getWeekStartDay(context)
        val weekStart = WeekUtils.weekStartFor(today, weekStartDay)
        val weekEntries = repo?.entriesForWeekOnce(weekStart).orEmpty()
        val nowM = LocalTime.now().hour * 60 + LocalTime.now().minute
        val paused = SessionPausePreferences.isPaused(context, today.toEpochDay())
        val freezeMs = SessionPausePreferences.freezeEpochMillis(context, today.toEpochDay())
        val pauseFreezeMinutes: Int? =
            if (paused) SessionPause.effectiveNowMinutes(nowM, freezeMs, today) else null
        val weekHours = SessionElapsed.weekActualHours(
            weekEntries, today.toEpochDay(), nowM, pauseFreezeMinutes
        )
        val goal = ReminderPreferences.getWeeklyGoalHours(context)
        return WidgetContent.build(
            todayIn = todayEntry?.clockInMinutes,
            todayOut = todayEntry?.clockOutMinutes,
            todayHoursWorked = todayEntry?.hoursWorked ?: 0.0,
            overnightPending = overnight,
            weekHours = weekHours,
            weekGoalHours = goal,
            todayEntry = todayEntry,
            nowMinutes = nowM,
            pauseFreezeMinutes = pauseFreezeMinutes,
            sessionPaused = paused
        )
    }

    fun scheduleOpenSessionRefresh(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = manager.getAppWidgetIds(
            ComponentName(appContext, WorkHoursWidgetProvider::class.java)
        )
        if (ids.isEmpty()) {
            cancelOpenSessionRefresh(appContext)
            return
        }
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = openRefreshPendingIntent(appContext)
        val trigger = SystemClock.elapsedRealtime() + OPEN_REFRESH_MS
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi)
        }
    }

    fun cancelOpenSessionRefresh(context: Context) {
        val appContext = context.applicationContext
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(openRefreshPendingIntent(appContext))
    }

    private fun openRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetRefreshReceiver::class.java).apply {
            action = WidgetRefreshReceiver.ACTION_OPEN_SESSION_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
