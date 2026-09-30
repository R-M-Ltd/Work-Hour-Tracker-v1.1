package com.rmltd.workhourstracker.ui

import com.rmltd.workhourstracker.ui.navigation.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsSectionNavTest {

    @Test
    fun fromNavArg_parsesSectionsAndNone() {
        assertNull(SettingsSection.fromNavArg(null))
        assertNull(SettingsSection.fromNavArg(""))
        assertNull(SettingsSection.fromNavArg("none"))
        assertNull(SettingsSection.fromNavArg("NONE"))
        assertEquals(SettingsSection.EXPORT, SettingsSection.fromNavArg("EXPORT"))
        assertEquals(SettingsSection.RATES_GOALS, SettingsSection.fromNavArg("rates_goals"))
        assertEquals(SettingsSection.APPEARANCE, SettingsSection.fromNavArg("Appearance"))
        assertEquals(SettingsSection.BACKUP_CLOUD, SettingsSection.fromNavArg("BACKUP_CLOUD"))
        assertEquals(SettingsSection.REMINDERS_SHADE, SettingsSection.fromNavArg("REMINDERS_SHADE"))
        assertNull(SettingsSection.fromNavArg("unknown"))
    }

    @Test
    fun routesSettingsHelper_encodesSectionOrNone() {
        val nav = File("src/main/java/com/rmltd/workhourstracker/ui/navigation/AppNavigation.kt")
            .readText()
        assertTrue(nav.contains("fun settings(section: SettingsSection? = null)"))
        assertTrue(nav.contains("settings?section="))
        assertTrue(nav.contains("section?.name ?: \"none\""))
    }
}
