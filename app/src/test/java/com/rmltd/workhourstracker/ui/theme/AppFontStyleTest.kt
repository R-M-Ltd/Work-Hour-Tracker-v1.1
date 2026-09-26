package com.rmltd.workhourstracker.ui.theme

import androidx.compose.ui.text.font.FontFamily
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
    fun fiveNamedStylesWithStableKeys() {
        val expected = listOf(
            "default" to "Default",
            "sans_serif" to "Sans Serif",
            "serif" to "Serif",
            "monospace" to "Monospace",
            "cursive" to "Cursive"
        )
        assertEquals(5, AppFontStyle.entries.size)
        assertEquals(expected, AppFontStyle.entries.map { it.key to it.displayName })
    }

    @Test
    fun fromKeyResolvesKnownKeysCaseInsensitive() {
        assertEquals(AppFontStyle.SANS_SERIF, AppFontStyle.fromKey("sans_serif"))
        assertEquals(AppFontStyle.SERIF, AppFontStyle.fromKey("SERIF"))
        assertEquals(AppFontStyle.MONOSPACE, AppFontStyle.fromKey("Monospace"))
        assertEquals(AppFontStyle.DEFAULT, AppFontStyle.fromKey("default"))
        assertEquals(AppFontStyle.CURSIVE, AppFontStyle.fromKey("cursive"))
        assertEquals(AppFontStyle.CURSIVE, AppFontStyle.fromKey("CURSIVE"))
    }

    @Test
    fun fromKeyFallsBackToDefaultForNullOrUnknown() {
        assertEquals(AppFontStyle.DEFAULT, AppFontStyle.fromKey(null))
        assertEquals(AppFontStyle.DEFAULT, AppFontStyle.fromKey(""))
        assertEquals(AppFontStyle.DEFAULT, AppFontStyle.fromKey("comic_sans"))
    }

    @Test
    fun toFontFamilyMapsPlatformConstants() {
        assertSame(FontFamily.Default, AppFontStyle.DEFAULT.toFontFamily())
        assertSame(FontFamily.SansSerif, AppFontStyle.SANS_SERIF.toFontFamily())
        assertSame(FontFamily.Serif, AppFontStyle.SERIF.toFontFamily())
        assertSame(FontFamily.Monospace, AppFontStyle.MONOSPACE.toFontFamily())
        assertSame(FontFamily.Cursive, AppFontStyle.CURSIVE.toFontFamily())
    }
}
