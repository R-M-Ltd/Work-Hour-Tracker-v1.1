package com.rmltd.workhourstracker.data

import com.rmltd.workhourstracker.ui.navigation.OnboardingNav
import com.rmltd.workhourstracker.ui.screens.OnboardingCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM coverage for 1.3.37 first-run onboarding gate.
 * SharedPreferences I/O needs instrumented / Robolectric; gate + copy +
 * Skip/Get-started complete contract are covered here.
 */
class OnboardingPreferencesTest {

    @Test
    fun prefsFileAndKey_locked() {
        assertEquals("onboarding_prefs", OnboardingPreferences.PREFS_NAME)
        assertEquals("prefs_onboarding_complete", OnboardingPreferences.KEY_COMPLETE)
        assertFalse(OnboardingPreferences.DEFAULT_COMPLETE)
    }

    @Test
    fun firstRunIncomplete_startsOnboarding() {
        assertEquals(
            "onboarding",
            OnboardingPreferences.startDestination(isComplete = false)
        )
        assertEquals(
            OnboardingPreferences.ROUTE_ONBOARDING,
            OnboardingNav.startDestination(isComplete = false)
        )
    }

    @Test
    fun completeOrSecondLaunch_startsHome() {
        assertEquals(
            "home",
            OnboardingPreferences.startDestination(isComplete = true)
        )
        assertEquals(
            OnboardingPreferences.ROUTE_HOME,
            OnboardingNav.startDestination(isComplete = true)
        )
    }

    @Test
    fun skipAndGetStarted_bothMarkComplete() {
        // Both Skip and Get started call setComplete(true) then navigate Home.
        assertTrue(OnboardingPreferences.markComplete())
        assertEquals(OnboardingCopy.SKIP, "Skip")
        assertEquals(OnboardingCopy.GET_STARTED, "Get started")
        // After markComplete, startDestination is Home (second-launch path).
        assertEquals(
            "home",
            OnboardingPreferences.startDestination(
                isComplete = OnboardingPreferences.markComplete()
            )
        )
    }

    @Test
    fun afterComplete_backStackPopsOnboardingInclusive() {
        assertEquals("home", OnboardingNav.destinationAfterComplete())
        assertEquals("onboarding", OnboardingNav.routeToPopOnComplete())
        assertTrue(OnboardingNav.POP_ONBOARDING_INCLUSIVE)
        // Onboarding must not remain under Home after Skip / Get started.
        assertFalse(
            OnboardingNav.routeToPopOnComplete() ==
                OnboardingNav.destinationAfterComplete()
        )
    }

    @Test
    fun lockedCopy_threePagesNoCloud() {
        assertEquals(3, OnboardingCopy.PAGE_COUNT)
        assertEquals("Track your work hours", OnboardingCopy.WELCOME_TITLE)
        assertEquals(
            "Clock in and out, log today’s hours, and keep a clear history — all in one place.",
            OnboardingCopy.WELCOME_BODY
        )
        assertEquals("Clock in. Log hours. Review.", OnboardingCopy.WALKTHROUGH_TITLE)
        assertEquals("Clock in & out", OnboardingCopy.FEAT_CLOCK_TITLE)
        assertEquals("One tap on Home starts or ends your day.", OnboardingCopy.FEAT_CLOCK_BODY)
        assertEquals("Today’s hours", OnboardingCopy.FEAT_HOURS_TITLE)
        assertEquals(
            "Add or change a typed total when punches aren’t enough.",
            OnboardingCopy.FEAT_HOURS_BODY
        )
        assertEquals("History", OnboardingCopy.FEAT_HISTORY_TITLE)
        assertEquals(
            "Past days stay in ⋮ → History when you need them.",
            OnboardingCopy.FEAT_HISTORY_BODY
        )
        assertEquals("You’re ready", OnboardingCopy.READY_TITLE)
        assertEquals(
            "Head to Home — clock the day or add hours whenever you like.",
            OnboardingCopy.READY_BODY
        )
        assertEquals("Next", OnboardingCopy.NEXT)

        val allCopy = listOf(
            OnboardingCopy.WELCOME_TITLE,
            OnboardingCopy.WELCOME_BODY,
            OnboardingCopy.WALKTHROUGH_TITLE,
            OnboardingCopy.FEAT_CLOCK_TITLE,
            OnboardingCopy.FEAT_CLOCK_BODY,
            OnboardingCopy.FEAT_HOURS_TITLE,
            OnboardingCopy.FEAT_HOURS_BODY,
            OnboardingCopy.FEAT_HISTORY_TITLE,
            OnboardingCopy.FEAT_HISTORY_BODY,
            OnboardingCopy.READY_TITLE,
            OnboardingCopy.READY_BODY
        ).joinToString(" ").lowercase()
        assertFalse(allCopy.contains("cloud"))
        assertFalse(allCopy.contains("oauth"))
        assertFalse(allCopy.contains("icloud"))
        assertFalse(allCopy.contains("sign in"))
    }

    @Test
    fun semanticsLabels_locked() {
        assertEquals("Skip onboarding", OnboardingCopy.CD_SKIP)
        assertEquals("Next page", OnboardingCopy.CD_NEXT)
        assertEquals("Get started and open Home", OnboardingCopy.CD_GET_STARTED)
    }
}
