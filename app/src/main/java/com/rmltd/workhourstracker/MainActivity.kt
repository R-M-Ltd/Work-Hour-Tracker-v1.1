package com.rmltd.workhourstracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rmltd.workhourstracker.data.CloudSyncPreferences
import com.rmltd.workhourstracker.data.sync.CloudOAuthLauncher
import com.rmltd.workhourstracker.data.sync.CloudSyncEngine
import com.rmltd.workhourstracker.ui.navigation.AppNavHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import com.rmltd.workhourstracker.data.AppearanceMode
import com.rmltd.workhourstracker.ui.theme.WorkHoursTheme
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModel
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModelFactory

class MainActivity : ComponentActivity() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val viewModel: WorkHoursViewModel by viewModels {
        WorkHoursViewModelFactory(
            (application as WorkHoursApplication).repository,
            applicationContext
        )
    }

    /**
     * When the app stays open across local midnight (or TZ changes), recompute
     * weekStart / Home anchor without requiring a process kill. Also fired on
     * Lifecycle ON_START (single path — not also onResume) so returning always refreshes.
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
            ioScope.launch {
                runCatching {
                    CloudSyncEngine(
                        (application as WorkHoursApplication).repository
                    ).syncOnResumeIfNeeded(applicationContext)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(lifecycleRefreshObserver)
        handleOAuthIntent(intent)
        setContent {
            val colorTheme by viewModel.colorTheme.collectAsState()
            val fontStyle by viewModel.fontStyle.collectAsState()
            val appearanceMode by viewModel.appearanceMode.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = appearanceMode.resolveDark(systemDark)
            WorkHoursTheme(theme = colorTheme, fontStyle = fontStyle, darkTheme = darkTheme) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "com.rmltd.workhourstracker" || uri.host != "oauth") return
        val provider = CloudSyncPreferences.getProvider(this)
            ?: CloudSyncPreferences.getPendingPkceProvider(this)
            ?: return
        ioScope.launch {
            val ok = runCatching {
                CloudOAuthLauncher.applyAuthRedirect(this@MainActivity, uri, provider)
            }.getOrDefault(false)
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    if (ok) "Cloud linked" else "Sign-in did not return a token",
                    Toast.LENGTH_SHORT
                ).show()
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
