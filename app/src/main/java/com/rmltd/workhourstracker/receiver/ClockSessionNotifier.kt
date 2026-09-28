package com.rmltd.workhourstracker.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rmltd.workhourstracker.MainActivity
import com.rmltd.workhourstracker.R
import com.rmltd.workhourstracker.WorkHoursApplication
import com.rmltd.workhourstracker.data.ClockDayState
import com.rmltd.workhourstracker.data.ShadePreferences
import com.rmltd.workhourstracker.util.HoursCalc
import com.rmltd.workhourstracker.util.SessionElapsed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Ongoing shade notification while clocked in, when Settings
 * "Notification clock controls" is on. Channel [CHANNEL_ID] ≠ EOD/daily.
 */
object ClockSessionNotifier {

    const val CHANNEL_ID = "clock_session"
    const val CHANNEL_NAME = "Clock session"
    const val NOTIFICATION_ID = 2004

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing clock-in controls in the notification shade"
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** Fire-and-forget sync from punch paths / Settings. */
    fun syncFromApp(context: Context) {
        val appContext = context.applicationContext
        scope.launch { sync(appContext) }
    }

    suspend fun sync(context: Context) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        if (!ShadePreferences.isClockControlsEnabled(appContext)) {
            cancel(appContext)
            return
        }
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            cancel(appContext)
            return
        }
        val app = appContext as? WorkHoursApplication
        val repo = app?.repository ?: run {
            cancel(appContext)
            return
        }
        val today = LocalDate.now()
        val todayEntry = repo.entryForDateOnce(today)
        val openEntry = repo.findOpenEntryOnce()
        val overnightOtherDay = openEntry != null &&
            openEntry.dateEpochDay != today.toEpochDay() &&
            ClockDayState.isOvernightOpen(openEntry.clockInMinutes, openEntry.clockOutMinutes)
        val todayOpen = todayEntry?.clockInMinutes != null && todayEntry.clockOutMinutes == null
        if (!todayOpen) {
            cancel(appContext)
            return
        }
        // Overnight on another day while somehow today also open is rare; still show today.
        val inM = todayEntry!!.clockInMinutes!!
        val nowM = LocalTime.now().hour * 60 + LocalTime.now().minute
        val elapsed = SessionElapsed.todayDisplayHours(todayEntry, nowM) ?: 0.0
        val text =
            "Clocked in since ${HoursCalc.formatClock(inM)} · ${HoursCalc.formatHours(elapsed)}"
        post(appContext, text, overnightOtherDay)
    }

    private fun post(context: Context, text: String, overnightPending: Boolean) {
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val clockOutIntent = Intent(context, ClockSessionActionReceiver::class.java).apply {
            action = ClockSessionActionReceiver.ACTION_CLOCK_OUT
        }
        val clockOutPending = PendingIntent.getBroadcast(
            context, 41, clockOutIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        // Overnight → Clock out opens app instead of silent punch (action still present;
        // receiver routes to open app when overnight).
        builder.addAction(0, "Clock out", clockOutPending)
        if (overnightPending) {
            // Prefer opening app for overnight copy clarity in expanded text
            builder.setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$text\nOvernight open elsewhere — Clock out opens the app."
                )
            )
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
