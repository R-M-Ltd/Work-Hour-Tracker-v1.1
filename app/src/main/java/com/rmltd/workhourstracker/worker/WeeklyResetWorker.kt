package com.rmltd.workhourstracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rmltd.workhourstracker.WorkHoursApplication
import com.rmltd.workhourstracker.widget.WorkHoursWidgetUpdater

/**
 * Triggered by [com.rmltd.workhourstracker.receiver.WeeklyResetReceiver]
 * (and [com.rmltd.workhourstracker.receiver.BootReceiver] catch-up).
 * Runs through WorkManager (rather than doing the DB write directly in the
 * BroadcastReceiver) so the archive operation reliably completes even if the
 * device is briefly busy or the app process gets killed right after the alarm.
 * Refreshes the home-screen widget **after** [WorkHoursRepository.catchUpWeekArchives]
 * so week totals are not briefly stale (receiver intentionally skips early refresh).
 */
class WeeklyResetWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = (applicationContext as WorkHoursApplication).repository
            repository.catchUpWeekArchives()
            WorkHoursWidgetUpdater.requestUpdate(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
