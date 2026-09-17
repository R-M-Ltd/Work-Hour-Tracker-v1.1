package com.rmltd.workhourstracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rmltd.workhourstracker.ui.navigation.AppNavHost
import com.rmltd.workhourstracker.ui.theme.WorkHoursTheme
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModel
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: WorkHoursViewModel by viewModels {
        WorkHoursViewModelFactory(
            (application as WorkHoursApplication).repository,
            applicationContext
        )
    }

    /**
     * When the app stays open across local midnight (or TZ changes), recompute
     * weekStart / Home anchor without requiring a process kill. Also fired on
     * Lifecycle ON_START so returning to the activity always refreshes.
     */
    private val dateChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.onAppResume()
        }
    }

    private var dateReceiverRegistered = false

    private val lifecycleRefreshObserver = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) {
            viewModel.onAppResume()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(lifecycleRefreshObserver)
        setContent {
            WorkHoursTheme {
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* No-op either way — the app is fully usable without notifications. */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                AppNavHost(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerDateChangeReceiver()
    }

    override fun onStop() {
        unregisterDateChangeReceiver()
        super.onStop()
    }

    override fun onDestroy() {
        lifecycle.removeObserver(lifecycleRefreshObserver)
        super.onDestroy()
    }

    /** Kept so resume-from-paused (same Activity, no stop) still refreshes. */
    override fun onResume() {
        super.onResume()
        viewModel.onAppResume()
    }

    private fun registerDateChangeReceiver() {
        if (dateReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dateChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(dateChangeReceiver, filter)
        }
        dateReceiverRegistered = true
    }

    private fun unregisterDateChangeReceiver() {
        if (!dateReceiverRegistered) return
        try {
            unregisterReceiver(dateChangeReceiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
        dateReceiverRegistered = false
    }
}
