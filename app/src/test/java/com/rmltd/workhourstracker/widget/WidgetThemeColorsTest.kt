package com.rmltd.workhourstracker.widget

import com.rmltd.workhourstracker.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM coverage for widget chrome color mapping (no RemoteViews / Bitmap).
 * Hexes match Theme.kt / palettes.json locked roles.
 */
class WidgetThemeColorsTest {

    @Test
    fun purpleLightMatchesLockedPrimary() {
        val c = WidgetThemeColors.resolve(AppTheme.PURPLE, dark = false)
        assertEquals(0xFF5B3F9E.toInt(), c.primary)
        assertEquals(0xFFE9DDFF.toInt(), c.primaryContainer)
        assertEquals(0xFF1D1A22.toInt(), c.onSurface)
        assertEquals(0xFF49454E.toInt(), c.onSurfaceVariant)
    }

    @Test
    fun aquaDarkMatchesPackNote() {
        val c = WidgetThemeColors.resolve(AppTheme.AQUA, dark = true)
        assertEquals(0xFF4DD0E1.toInt(), c.primary)
        assertEquals(0xFF006064.toInt(), c.primaryContainer)
        assertEquals(0xFFDEE3E4.toInt(), c.onSurface)
        assertEquals(0xFFBEC8CA.toInt(), c.onSurfaceVariant)
    }

    @Test
    fun aquaLightPrimary() {
        val c = WidgetThemeColors.resolve(AppTheme.AQUA, dark = false)
        assertEquals(0xFF00838F.toInt(), c.primary)
        assertEquals(0xFFB2EBF2.toInt(), c.primaryContainer)
    }

    @Test
    fun allSixThemesResolveLightAndDark() {
        for (theme in AppTheme.entries) {
            val light = WidgetThemeColors.resolve(theme, dark = false)
            val dark = WidgetThemeColors.resolve(theme, dark = true)
            // Non-zero alpha channel
            assertEquals(0xFF, (light.primary ushr 24) and 0xFF)
            assertEquals(0xFF, (dark.primary ushr 24) and 0xFF)
        }
    }
}
