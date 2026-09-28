package com.rmltd.workhourstracker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rmltd.workhourstracker.data.OnboardingPreferences
import com.rmltd.workhourstracker.ui.screens.EntryScreen
import com.rmltd.workhourstracker.ui.screens.HomeScreen
import com.rmltd.workhourstracker.ui.screens.LogScreen
import com.rmltd.workhourstracker.ui.screens.OnboardingScreen
import com.rmltd.workhourstracker.ui.screens.SettingsScreen
import com.rmltd.workhourstracker.util.WeekUtils
import com.rmltd.workhourstracker.viewmodel.WorkHoursViewModel
import java.time.LocalDate

internal object Routes {
    const val ONBOARDING = OnboardingPreferences.ROUTE_ONBOARDING
    const val HOME = OnboardingPreferences.ROUTE_HOME
    const val LOG = "log"
    const val SETTINGS = "settings"
    const val ENTRY_ARG = "epochDay"
    const val ENTRY = "entry/{$ENTRY_ARG}"
    fun entry(date: LocalDate) = "entry/${date.toEpochDay()}"
}

/**
 * After Skip / Get started: set complete flag then land on Home with onboarding
 * popped (inclusive) so system back does not reopen onboarding.
 * Pure helper for unit tests — mirrors the NavOptions used in [AppNavHost].
 */
object OnboardingNav {
    const val POP_ONBOARDING_INCLUSIVE = true

    fun startDestination(isComplete: Boolean): String =
        OnboardingPreferences.startDestination(isComplete)

    /** Route left on the back stack after completing onboarding. */
    fun destinationAfterComplete(): String = Routes.HOME

    /** Route that must be removed (inclusive) so back cannot return to it. */
    fun routeToPopOnComplete(): String = Routes.ONBOARDING
}

@Composable
fun AppNavHost(
    viewModel: WorkHoursViewModel,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    // Read once before NavHost so Home is not the startDestination underneath onboarding.
    val startDestination = remember {
        OnboardingPreferences.startDestination(OnboardingPreferences.isComplete(context))
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    OnboardingPreferences.setComplete(context, true)
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onDayClick = { date -> navController.navigate(Routes.entry(date)) },
                onViewLog = { navController.navigate(Routes.LOG) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onLogLunch = { navController.navigate(Routes.entry(LocalDate.now())) }
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
            LogScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEditDay = { date -> navController.navigate(Routes.entry(date)) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                // L2 1.3.35: Set today's times is hosted as AddChangeHoursSheet on Settings
                // (not Routes.entry). Lunch CTA stays Entry nav-only.
                onSetTodaysTimes = { /* sheet hosted inside SettingsScreen */ },
                onLogLunch = {
                    navController.navigate(Routes.entry(LocalDate.now())) {
                        popUpTo(Routes.HOME)
                    }
                }
            )
        }
    }
}
