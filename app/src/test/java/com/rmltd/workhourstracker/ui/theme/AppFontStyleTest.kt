package com.rmltd.workhourstracker.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure JVM coverage for [AppFontStyle] stable keys / defaults (no Context needed).
 */
class AppFontStyleTest {

    @Test
    fun defaultIsDefaultStyle() {
        assertSame(AppFontStyle.DEFAULT, AppFontStyle.DEFAULT_STYLE)
        assertEquals("default", AppFontStyle.DEFAULT_STYLE.key)
        assertEquals("Default", AppFontStyle.DEFAULT_STYLE.displayName)
    }

    @Test
    fun fourNamedStylesWithStableKeys() {
        val expected = listOf(
            "default" to "Default",
            "sans_serif" to "Sans Serif",
            "serif" to "Serif",
            "monospace" to "Monospace"
        )
        assertEquals(4, AppFontStyle.entries.size)
        assertEquals(expected, AppFontStyle.entries.map { it.key to it.displayName })
    }

    @Test
    fun fromKeyResolvesKnownKeysCaseInsensitive() {
        assertEquals(AppFontStyle.SANS_SERIF, AppFontStyle.fromKey("sans_serif"))
        assertEquals(AppFontStyle.SERIF, AppFontStyle.fromKey("SERIF"))
        assertEquals(AppFontStyle.MONOSPACE, AppFontStyle.fromKey("Monospace"))
        assertEquals(AppFontStyle.DEFAULT, AppFontStyle.fromKey("default"))
    }

    @Test
    fun fromKeyFallsBackToDefaultForNullOrUnknown() {
        assertEquals(AppFontStyle.DEFAULT, AppFontStyle.fromKey(null))
        assertEquals(AppFontStyle.DEFAULT, AppFontStyle.fromKey(""))
        assertEquals(AppFontStyle.DEFAULT, AppFontStyle.fromKey("comic_sans"))
    }
}
