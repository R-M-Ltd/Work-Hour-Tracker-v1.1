package com.rmltd.workhourstracker.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rmltd.workhourstracker.data.OnboardingPreferences
import com.rmltd.workhourstracker.ui.components.MoreMenuSheet
import com.rmltd.workhourstracker.ui.screens.EntryScreen
import com.rmltd.workhourstracker.ui.screens.HomeMoreAction
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

/** Bottom-bar tab routes that show the NavigationBar (Nav A). */
object MainTabs {
    val ROUTES = setOf(Routes.HOME, Routes.LOG)

    fun showsBottomBar(route: String?): Boolean =
        route != null && (route == Routes.HOME || route == Routes.LOG)
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

    var showMoreSheet by remember { mutableStateOf(false) }
    var homeMoreAction by remember { mutableStateOf<HomeMoreAction?>(null) }
    var moreSelected by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBar = MainTabs.showsBottomBar(currentRoute)
    val homeClock by viewModel.homeClockUi.collectAsState()
    val clockBusy by viewModel.clockOpInProgress.collectAsState()

    fun navigateTab(route: String) {
        moreSelected = false
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openSettingsFromMore() {
        showMoreSheet = false
        moreSelected = false
        navController.navigate(Routes.SETTINGS)
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar(
                    modifier = Modifier.semantics { contentDescription = "Main navigation" }
                ) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME && !moreSelected,
                        onClick = { navigateTab(Routes.HOME) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        colors = colors
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.LOG && !moreSelected,
                        onClick = { navigateTab(Routes.LOG) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History") },
                        label = { Text("History") },
                        colors = colors
                    )
                    NavigationBarItem(
                        selected = moreSelected,
                        onClick = {
                            moreSelected = true
                            showMoreSheet = true
                        },
                        icon = { Icon(Icons.Filled.MoreHoriz, contentDescription = "More") },
                        label = { Text("More") },
                        colors = colors
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {

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
                    onViewLog = { navigateTab(Routes.LOG) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onLogLunch = { navController.navigate(Routes.entry(LocalDate.now())) },
                    onOpenMore = {
                        moreSelected = true
                        showMoreSheet = true
                    },
                    pendingMoreAction = homeMoreAction,
                    onPendingMoreActionConsumed = { homeMoreAction = null }
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
                    onBack = {
                        // Tab root: go Home rather than popping to empty.
                        navigateTab(Routes.HOME)
                    },
                    onEditDay = { date -> navController.navigate(Routes.entry(date)) },
                    isTabRoot = true
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

    if (showMoreSheet) {
        MoreMenuSheet(
            forgotClockOutEnabled = homeClock.clockOutEnabled && !clockBusy,
            onDismiss = {
                showMoreSheet = false
                moreSelected = false
            },
            onAddChangeHours = {
                showMoreSheet = false
                moreSelected = false
                navigateTab(Routes.HOME)
                homeMoreAction = HomeMoreAction.ADD_CHANGE_HOURS
            },
            onLogLunch = {
                showMoreSheet = false
                moreSelected = false
                navController.navigate(Routes.entry(LocalDate.now()))
            },
            onSetTodaysTimes = {
                showMoreSheet = false
                moreSelected = false
                navigateTab(Routes.HOME)
                homeMoreAction = HomeMoreAction.SET_TODAYS_TIMES
            },
            onForgotClockOut = {
                showMoreSheet = false
                moreSelected = false
                navigateTab(Routes.HOME)
                homeMoreAction = HomeMoreAction.FORGOT_CLOCK_OUT
            },
            onHistory = {
                showMoreSheet = false
                moreSelected = false
                navigateTab(Routes.LOG)
            },
            onExport = { openSettingsFromMore() },
            onRatesAndGoals = { openSettingsFromMore() },
            onAppearance = { openSettingsFromMore() },
            onBackupAndCloud = { openSettingsFromMore() },
            onRemindersAndShade = { openSettingsFromMore() },
            onSettings = { openSettingsFromMore() }
        )
    }
}
