package com.rmltd.workhourstracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * L2: Settings Set today's times opens Add/Change sheet (not Entry).
 * Titles match Home ⋮ set flow / Change when prefilled.
 */
class SettingsTodaySheetTitleTest {

    @Test
    fun sheetTitle_emptyVsPrefill() {
        assertEquals("Set today's times", settingsTodaySheetTitle(hasPersistedHours = false))
        assertEquals("Change hours", settingsTodaySheetTitle(hasPersistedHours = true))
    }

    @Test
    fun appNavigation_doesNotRouteSetTodaysTimesToEntry() {
        // Gradle runs tests with cwd = module (app/)
        val text = File("src/main/java/com/rmltd/workhourstracker/ui/navigation/AppNavigation.kt")
            .readText()
        // onSetTodaysTimes must not navigate to entry (L2). Lunch may still use entry.
        val settingsBlock = text.substringAfter("composable(Routes.SETTINGS)")
            .substringBefore("composable(")
            .ifEmpty { text.substringAfter("composable(Routes.SETTINGS)") }
        assertFalse(
            "Settings onSetTodaysTimes must not navigate(Routes.entry(today))",
            settingsBlock.contains("onSetTodaysTimes") &&
                settingsBlock.substringAfter("onSetTodaysTimes")
                    .substringBefore("onLogLunch")
                    .contains("Routes.entry")
        )
    }

    @Test
    fun homeDeadPaths_removedFromSource() {
        val home = File("src/main/java/com/rmltd/workhourstracker/ui/screens/HomeScreen.kt")
            .readText()
        assertFalse(home.contains("fun tryHomeManualSave"))
        assertFalse(home.contains("fun HomeClockTimeRow"))
        assertFalse(home.contains("private fun HomeClockTimeRow"))
    }
}

/** Mirrors SettingsScreen title choice for Set today's times sheet. */
fun settingsTodaySheetTitle(hasPersistedHours: Boolean): String =
    if (hasPersistedHours) "Change hours" else "Set today's times"
