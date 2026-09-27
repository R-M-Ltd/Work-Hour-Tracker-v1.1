package com.rmltd.workhourstracker.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
    fun provider_fromId() {
        assertEquals(
            CloudSyncPreferences.Provider.GOOGLE_DRIVE,
            CloudSyncPreferences.Provider.fromId("google_drive")
        )
        assertEquals(null, CloudSyncPreferences.Provider.fromId("icloud"))
    }
}
