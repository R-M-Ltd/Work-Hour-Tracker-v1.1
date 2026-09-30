package com.rmltd.workhourstracker.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level protect tests for 1.3.40 timer-first Home (placement only).
 */
class TimerFirstHomeProtectTest {

    private fun read(rel: String): String {
        val candidates = listOf(
            File("src/main/java/com/rmltd/workhourstracker/$rel"),
            File("app/src/main/java/com/rmltd/workhourstracker/$rel"),
            File("../app/src/main/java/com/rmltd/workhourstracker/$rel")
        )
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun homeHasNoWeeklyGoalsCard() {
        val home = read("ui/screens/HomeScreen.kt")
        assertFalse(
            "Goals card must be removed from Home (Idle B)",
            home.contains("contentDescription = \"Weekly goals\"")
        )
        assertFalse(home.contains("This week goal"))
        assertFalse(home.contains("Edit goal in Settings"))
    }

    @Test
    fun homeIdleHasWidePillClockIn() {
        val home = read("ui/screens/HomeScreen.kt")
        assertTrue(home.contains("fillMaxWidth(0.85f)"))
        assertTrue(home.contains("contentDescription = \"Clock in\""))
        assertTrue(home.contains("Not clocked in"))
    }

    @Test
    fun homeRunningHasPauseAndStopPeers() {
        val home = read("ui/screens/HomeScreen.kt")
        assertTrue(home.contains("contentDescription = \"Pause\""))
        assertTrue(home.contains("contentDescription = \"Stop\""))
        assertTrue(home.contains("contentDescription = \"Resume\""))
        assertTrue(home.contains("SessionPausePreferences.pause"))
        // Must not fake pause via clockOut
        val pauseFn = home.substringAfter("fun doPause()").substringBefore("fun doResume()")
        assertFalse(pauseFn.contains("clockOutNow"))
        assertFalse(pauseFn.contains("clockOutAt"))
    }

    @Test
    fun moreSheetGroupedSections() {
        val more = read("ui/components/MoreMenuSheet.kt")
        assertTrue(more.contains("\"Today\""))
        assertTrue(more.contains("Log & pay") || more.contains("\"Log & pay\"") || more.contains("Log & pay".uppercase()) || more.contains("LOG & PAY") || more.contains("SectionHeader(\"Log & pay\")"))
        assertTrue(more.contains("SectionHeader(\"App\")") || more.contains("\"App\""))
        assertTrue(more.contains("Add / Change hours"))
        assertTrue(more.contains("Rates & goals"))
        assertTrue(more.contains("Log lunch / break"))
        assertTrue(more.contains("Nav-only"))
    }

    @Test
    fun navAThreeTabBottomBar() {
        val nav = read("ui/navigation/AppNavigation.kt")
        assertTrue(nav.contains("NavigationBar"))
        assertTrue(nav.contains("\"Home\""))
        assertTrue(nav.contains("\"History\""))
        assertTrue(nav.contains("\"More\""))
        assertTrue(nav.contains("MoreMenuSheet"))
        assertTrue(nav.contains("popUpTo") && nav.contains("saveState"))
    }

    @Test
    fun lunchCtaStillNavOnlyFromMore() {
        val nav = read("ui/navigation/AppNavigation.kt")
        val lunchBlock = nav.substringAfter("onLogLunch = {").substringBefore("onSetTodaysTimes")
        assertTrue(lunchBlock.contains("Routes.entry(LocalDate.now())"))
        assertFalse(lunchBlock.contains("saveEntry"))
        assertFalse(lunchBlock.contains("breakPaid = true"))
    }

    @Test
    fun versionIs1340Vc42() {
        val gradle = listOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts")
        ).first { it.exists() }.readText()
        assertTrue(gradle.contains("versionName = \"1.3.40\""))
        assertTrue(gradle.contains("versionCode = 42"))
    }
}
