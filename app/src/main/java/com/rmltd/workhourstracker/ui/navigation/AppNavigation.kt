package com.rmltd.workhourstracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rmltd.workhourstracker.ui.screens.EntryScreen
import com.rmltd.workhourstracker.ui.screens.HomeScreen
import com.rmltd.workhourstracker.ui.screens.LogScreen
import com.rmltd.workhourstracker.ui.screens.SettingsScreen
import com.rmltd.workhourstracker.util.WeekUtils
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModel
import java.time.LocalDate

private object Routes {
    const val HOME = "home"
    const val LOG = "log"
    const val SETTINGS = "settings"
    const val ENTRY_ARG = "epochDay"
    const val ENTRY = "entry/{$ENTRY_ARG}"
    fun entry(date: LocalDate) = "entry/${date.toEpochDay()}"
}

@Composable
fun AppNavHost(
    viewModel: WorkHoursViewModel,
    navController: NavHostController = rememberNavController()
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onDayClick = { date -> navController.navigate(Routes.entry(date)) },
                onViewLog = { navController.navigate(Routes.LOG) },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.ENTRY,
            arguments = listOf(navArgument(Routes.ENTRY_ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getLong(Routes.ENTRY_ARG)
            val entryDate = WeekUtils.dateFromEpochDayOrToday(raw)
            EntryScreen(
                date = entryDate,
                viewModel = viewModel,
                onDone = { navController.popBackStack() },
                onEditOpenDay = { openDate ->
                    navController.navigate(Routes.entry(openDate)) {
                        popUpTo(Routes.entry(entryDate)) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOG) {
            LogScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
