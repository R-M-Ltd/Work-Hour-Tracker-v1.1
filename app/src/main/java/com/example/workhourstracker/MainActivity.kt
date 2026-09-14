package com.example.workhourstracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import com.example.workhourstracker.ui.navigation.AppNavHost
import com.example.workhourstracker.ui.theme.WorkHoursTheme
import com.example.workhourstracker.viewmodel.WorkHoursViewModel
import com.example.workhourstracker.viewmodel.WorkHoursViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: WorkHoursViewModel by viewModels {
        WorkHoursViewModelFactory((application as WorkHoursApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    override fun onResume() {
        super.onResume()
        viewModel.onAppResume()
    }
}
