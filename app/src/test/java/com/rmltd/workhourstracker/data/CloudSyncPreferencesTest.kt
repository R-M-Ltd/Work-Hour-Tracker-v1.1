package com.rmltd.workhourstracker.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncPreferencesTest {

    @Test
    fun defaultEnabled_isOff() {
        assertFalse(CloudSyncPreferences.defaultEnabled())
    }

    @Test
    fun conflictAndIcloudCopy_locked() {
        assertEquals(
            "If the same day changed on two devices, the latest save wins.",
            CloudSyncPreferences.CONFLICT_COPY
        )
        assertEquals(
            "iCloud is not available on Android.",
            CloudSyncPreferences.ICLOUD_FOOTNOTE
        )
        assertEquals(
            "Cloud provider not configured",
            CloudSyncPreferences.NOT_CONFIGURED
        )
    }

    @Test
    fun sessionExpiredAndAuthCodeCopy_locked() {
        assertEquals(
            "Session expired — sign in again to keep syncing.",
            CloudSyncPreferences.SESSION_EXPIRED
        )
        assertEquals("Sign in again", CloudSyncPreferences.SIGN_IN_AGAIN)
        assertEquals(
            "You'll approve access in your browser. We use a secure sign-in so sync can refresh without asking every time.",
            CloudSyncPreferences.AUTH_CODE_EDUCATION
        )
        assertEquals("Opening your cloud account…", CloudSyncPreferences.SIGNING_IN)
    }

    @Test
    fun isSessionExpiredSignal_flagOrLastError() {
        assertTrue(
            CloudSyncPreferences.isSessionExpiredSignal(
                CloudSyncPreferences.SESSION_EXPIRED,
                false
            )
        )
        assertTrue(CloudSyncPreferences.isSessionExpiredSignal(null, true))
        assertTrue(
            CloudSyncPreferences.isSessionExpiredSignal(
                CloudSyncPreferences.SESSION_EXPIRED,
                true
            )
        )
        assertFalse(CloudSyncPreferences.isSessionExpiredSignal("other error", false))
        assertFalse(CloudSyncPreferences.isSessionExpiredSignal(null, false))
    }

    @Test
    fun provider_fromId() {
        assertEquals(
            CloudSyncPreferences.Provider.GOOGLE_DRIVE,
            CloudSyncPreferences.Provider.fromId("google_drive")
        )
        assertEquals(null, CloudSyncPreferences.Provider.fromId("icloud"))
    }

    @Test
    fun helperChoose_locked() {
        assertEquals(
            "Choose one cloud. Switch providers by unlinking first.",
            CloudSyncPreferences.HELPER_CHOOSE
        )
    }
}
