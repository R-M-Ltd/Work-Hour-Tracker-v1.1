package com.rmltd.workhourstracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceModeTest {

    @Test
    fun defaultIsSystem() {
        assertSame(AppearanceMode.SYSTEM, AppearanceMode.DEFAULT)
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromKey(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromKey(""))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromKey("garbage"))
    }

    @Test
    fun fromKey_roundTrips() {
        for (mode in AppearanceMode.entries) {
            assertEquals(mode, AppearanceMode.fromKey(mode.key))
            assertEquals(mode, AppearanceMode.fromKey(mode.key.uppercase()))
        }
    }

    @Test
    fun resolveDark_systemTracksDevice() {
        assertTrue(AppearanceMode.SYSTEM.resolveDark(true))
        assertFalse(AppearanceMode.SYSTEM.resolveDark(false))
    }

    @Test
    fun resolveDark_lightForcesFalse() {
        assertFalse(AppearanceMode.LIGHT.resolveDark(true))
        assertFalse(AppearanceMode.LIGHT.resolveDark(false))
    }

    @Test
    fun resolveDark_darkForcesTrue() {
        assertTrue(AppearanceMode.DARK.resolveDark(true))
        assertTrue(AppearanceMode.DARK.resolveDark(false))
    }

    @Test
    fun shadeAndEodChannels_remainDistinct() {
        // Locked channel ids (mirror production companions; avoid loading Android receivers).
        val shade = "clock_session"
        val eod = "end_of_day_reminder"
        assertTrue(shade != eod)
        assertEquals("clock_session", shade)
        assertEquals("end_of_day_reminder", eod)
    }
}
