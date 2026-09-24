package com.rmltd.workhourstracker.ui.theme

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
    fun fiveNamedThemesWithStableKeys() {
        val expected = listOf(
            "purple" to "Purple",
            "blue" to "Blue",
            "red" to "Red",
            "green" to "Green",
            "orange" to "Orange"
        )
        assertEquals(5, AppTheme.entries.size)
        assertEquals(expected, AppTheme.entries.map { it.key to it.displayName })
    }

    @Test
    fun fromKeyResolvesKnownKeysCaseInsensitive() {
        assertEquals(AppTheme.BLUE, AppTheme.fromKey("blue"))
        assertEquals(AppTheme.RED, AppTheme.fromKey("RED"))
        assertEquals(AppTheme.GREEN, AppTheme.fromKey("Green"))
        assertEquals(AppTheme.ORANGE, AppTheme.fromKey("orange"))
        assertEquals(AppTheme.PURPLE, AppTheme.fromKey("purple"))
    }

    @Test
    fun fromKeyFallsBackToPurpleForNullOrUnknown() {
        assertEquals(AppTheme.PURPLE, AppTheme.fromKey(null))
        assertEquals(AppTheme.PURPLE, AppTheme.fromKey(""))
        assertEquals(AppTheme.PURPLE, AppTheme.fromKey("magenta"))
    }
}
