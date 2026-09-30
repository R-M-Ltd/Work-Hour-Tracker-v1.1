package com.rmltd.workhourstracker.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.rmltd.workhourstracker.MainActivity
import com.rmltd.workhourstracker.WorkHoursApplication
import com.rmltd.workhourstracker.data.ClockDayState
import com.rmltd.workhourstracker.data.ClockOutResult
import com.rmltd.workhourstracker.data.SessionPausePreferences
import com.rmltd.workhourstracker.widget.WorkHoursWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Shade "Clock out" action. Same punch path as Home; overnight → open app
 * instead of silent clock-out. Never posts on the EOD channel.
 */
class ClockSessionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CLOCK_OUT) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? WorkHoursApplication
                val repo = app?.repository
                if (repo == null) {
                    openApp(context)
                    return@launch
                }
                val today = LocalDate.now()
                val openEntry = repo.findOpenEntryOnce()
                val overnightOther = openEntry != null &&
                    openEntry.dateEpochDay != today.toEpochDay() &&
                    ClockDayState.isOvernightOpen(openEntry.clockInMinutes, openEntry.clockOutMinutes)
                if (overnightOther) {
                    // Do not silent-out overnight — open Home for resolve banner.
                    openApp(context)
                    return@launch
                }
                val now = LocalTime.now()
                val minutes = now.hour * 60 + now.minute
                val result = repo.clockOutNow(today, minutes)
                when (result) {
                    ClockOutResult.SUCCESS, ClockOutResult.SUCCESS_OVERNIGHT,
                    ClockOutResult.ALREADY_CLOSED -> {
                        SessionPausePreferences.clear(context)
                        WorkHoursWidgetUpdater.requestUpdate(context)
                        ClockSessionNotifier.sync(context)
                        toast(context, if (result == ClockOutResult.ALREADY_CLOSED) {
                            "Already clocked out"
                        } else {
                            "Clocked out"
                        })
                    }
                    ClockOutResult.FAILED, ClockOutResult.BUSY -> {
                        openApp(context)
                        toast(context, "Clock out failed — open the app")
                    }
                }
            } catch (_: Exception) {
                openApp(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun openApp(context: Context) {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // Prefer startActivity; PendingIntent for OEM consistency
        try {
            context.startActivity(open)
        } catch (_: Exception) {
            val pi = PendingIntent.getActivity(
                context, 42, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                pi.send()
            } catch (_: Exception) {
            }
        }
    }

    private fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_CLOCK_OUT = "com.rmltd.workhourstracker.ACTION_SHADE_CLOCK_OUT"
    }
}
