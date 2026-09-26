package com.rmltd.workhourstracker.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure JVM coverage for [AppTheme] stable keys / defaults (no Context needed).
 * SharedPreferences persistence for [com.rmltd.workhourstracker.data.ThemePreferences]
 * needs instrumented or Robolectric coverage.
 */
class AppThemeTest {

    @Test
    fun defaultIsPurple() {
        assertSame(AppTheme.PURPLE, AppTheme.DEFAULT)
        assertEquals("purple", AppTheme.DEFAULT.key)
        assertEquals("Purple", AppTheme.DEFAULT.displayName)
    }

    @Test
    fun sixNamedThemesWithStableKeys() {
        val expected = listOf(
            "purple" to "Purple",
            "blue" to "Blue",
            "red" to "Red",
            "green" to "Green",
            "orange" to "Orange",
            "aqua" to "Aqua"
        )
        assertEquals(6, AppTheme.entries.size)
        assertEquals(expected, AppTheme.entries.map { it.key to it.displayName })
    }

    @Test
    fun fromKeyResolvesKnownKeysCaseInsensitive() {
        assertEquals(AppTheme.BLUE, AppTheme.fromKey("blue"))
        assertEquals(AppTheme.RED, AppTheme.fromKey("RED"))
        assertEquals(AppTheme.GREEN, AppTheme.fromKey("Green"))
        assertEquals(AppTheme.ORANGE, AppTheme.fromKey("orange"))
        assertEquals(AppTheme.PURPLE, AppTheme.fromKey("purple"))
        assertEquals(AppTheme.AQUA, AppTheme.fromKey("aqua"))
        assertEquals(AppTheme.AQUA, AppTheme.fromKey("AQUA"))
    }

    @Test
    fun fromKeyFallsBackToPurpleForNullOrUnknown() {
        assertEquals(AppTheme.PURPLE, AppTheme.fromKey(null))
        assertEquals(AppTheme.PURPLE, AppTheme.fromKey(""))
        assertEquals(AppTheme.PURPLE, AppTheme.fromKey("magenta"))
    }

    @Test
    fun aquaPreviewPrimaryIsTealAqua() {
        assertEquals(Color(0xFF00838F), AppTheme.AQUA.previewPrimary())
    }

    @Test
    fun colorSchemeForAquaLightAndDarkPrimaries() {
        assertEquals(Color(0xFF00838F), colorSchemeFor(AppTheme.AQUA, darkTheme = false).primary)
        assertEquals(Color(0xFF4DD0E1), colorSchemeFor(AppTheme.AQUA, darkTheme = true).primary)
        assertEquals(Color(0xFFB2EBF2), colorSchemeFor(AppTheme.AQUA, darkTheme = false).primaryContainer)
        assertEquals(Color(0xFF006064), colorSchemeFor(AppTheme.AQUA, darkTheme = true).primaryContainer)
    }

    @Test
    fun lockedPurplePrimaryUnchanged() {
        assertEquals(Color(0xFF5B3F9E), AppTheme.PURPLE.previewPrimary())
        assertEquals(Color(0xFF5B3F9E), colorSchemeFor(AppTheme.PURPLE, darkTheme = false).primary)
    }
}
