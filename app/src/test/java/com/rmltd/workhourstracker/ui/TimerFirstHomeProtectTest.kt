package com.rmltd.workhourstracker.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level protect tests for timer-first Home + 1.3.41 lows (placement only).
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
        assertTrue(more.contains("SectionHeader(\"Day\")") || more.contains("\"Day\""))
        assertTrue(more.contains("SectionHeader(\"Pay\")") || more.contains("\"Pay\""))
        assertTrue(more.contains("SectionHeader(\"Settings\")") || more.contains("\"Settings\""))
        assertTrue(more.contains("Edit today"))
        assertTrue(more.contains("Rates & goals"))
        assertTrue(more.contains("Log lunch / break"))
        assertTrue(more.contains("Nav-only"))
        assertFalse("History row must be dropped from More", more.contains("title = \"History\""))
        assertFalse("Settings root row must be dropped", more.contains("title = \"Settings\""))
        assertFalse(more.contains("title = \"Add / Change hours\""))
        assertFalse(more.contains("title = \"Set today's times"))
        assertFalse(more.contains("onHistory"))
        assertFalse(more.contains("onSettings"))
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
        val moreBlock = nav.substringAfter("MoreMenuSheet(").substringBefore("onExport")
        val lunchBlock = moreBlock.substringAfter("onLogLunch = {").substringBefore("onForgotClockOut")
        assertTrue(lunchBlock.contains("Routes.entry(LocalDate.now())"))
        assertFalse(lunchBlock.contains("saveEntry"))
        assertFalse(lunchBlock.contains("breakPaid = true"))
    }

    @Test
    fun versionIs1342Vc44() {
        val gradle = listOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts")
        ).first { it.exists() }.readText()
        assertTrue(gradle.contains("versionName = \"1.3.42\""))
        assertTrue(gradle.contains("versionCode = 44"))
    }

    @Test
    fun homeDayRowRemoved() {
        val home = read("ui/screens/HomeScreen.kt")
        assertFalse(home.contains("private fun DayRow"))
        assertFalse(home.contains("fun DayRow"))
        assertFalse(home.contains("foundation.lazy.LazyColumn"))
        assertFalse(home.contains("foundation.lazy.items"))
    }

    @Test
    fun moreSettingsDeepLinksWired() {
        val nav = read("ui/navigation/AppNavigation.kt")
        assertTrue(nav.contains("openSettingsFromMore(SettingsSection.EXPORT)"))
        assertTrue(nav.contains("openSettingsFromMore(SettingsSection.RATES_GOALS)"))
        assertTrue(nav.contains("openSettingsFromMore(SettingsSection.APPEARANCE)"))
        assertTrue(nav.contains("openSettingsFromMore(SettingsSection.BACKUP_CLOUD)"))
        assertTrue(nav.contains("openSettingsFromMore(SettingsSection.REMINDERS_SHADE)"))
        // Settings root row dropped in 1.3.42 — deep-links only
        assertFalse(nav.contains("onSettings = { openSettingsFromMore(null) }"))
        assertTrue(nav.contains("HomeMoreAction.EDIT_TODAY"))
        assertTrue(nav.contains("initialSection = initialSection"))
        val settings = read("ui/screens/SettingsScreen.kt")
        assertTrue(settings.contains("initialSection: SettingsSection?"))
        assertTrue(settings.contains("SettingsSection.EXPORT"))
        assertTrue(settings.contains("animateScrollTo"))
    }

    @Test
    fun homeSecondaryTimesWired() {
        val home = read("ui/screens/HomeScreen.kt")
        assertTrue(home.contains("HomeQuietSecondaryTimes"))
        assertTrue(home.contains("HomeSecondaryTimes.shouldShow"))
        assertTrue(home.contains("HomeMoreAction.EDIT_TODAY"))
        assertTrue(home.contains("\"Edit today\""))
        assertTrue(home.contains("showForgotClockOut = true"))
    }

    @Test
    fun widgetTapOpensEditToday() {
        val updater = read("widget/WorkHoursWidgetUpdater.kt")
        assertTrue(updater.contains("EXTRA_OPEN_EDIT_TODAY"))
        assertTrue(updater.contains("putExtra(EXTRA_OPEN_EDIT_TODAY, true)"))
        val main = read("MainActivity.kt")
        assertTrue(main.contains("EXTRA_OPEN_EDIT_TODAY"))
        assertTrue(main.contains("openEditTodayTick"))
        val content = read("widget/WidgetContent.kt")
        assertTrue(content.contains("Start "))
        assertTrue(content.contains("Stop —"))
        assertTrue(content.contains("Paused · since"))
    }

    @Test
    fun widgetPauseFreezeWired() {
        val updater = read("widget/WorkHoursWidgetUpdater.kt")
        assertTrue(updater.contains("SessionPausePreferences.isPaused"))
        assertTrue(updater.contains("pauseFreezeMinutes"))
        assertTrue(updater.contains("SessionPause.effectiveNowMinutes"))
        val content = read("widget/WidgetContent.kt")
        assertTrue(content.contains("pauseFreezeMinutes: Int? = null"))
        assertTrue(content.contains("sessionPaused: Boolean = false"))
        assertTrue(content.contains("Paused · since"))
        assertTrue(content.contains("todayDisplayHours(entry, nowMinutes, pauseFreezeMinutes)"))
    }

    @Test
    fun shadeClockOutClearsPausePrefs() {
        val rx = read("receiver/ClockSessionActionReceiver.kt")
        assertTrue(rx.contains("SessionPausePreferences.clear(context)"))
        val success = rx.substringAfter("ClockOutResult.SUCCESS").substringBefore("ClockOutResult.FAILED")
        assertTrue(success.contains("SessionPausePreferences.clear"))
    }
}
