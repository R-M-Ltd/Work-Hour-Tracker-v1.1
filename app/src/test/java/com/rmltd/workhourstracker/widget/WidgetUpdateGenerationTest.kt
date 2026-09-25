package com.rmltd.workhourstracker.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure sequencing for M1: overlapping refreshes — only the latest generation applies.
 */
class WidgetUpdateGenerationTest {

    @Test
    fun nextToken_isMonotonic() {
        val g = WidgetUpdateGeneration()
        val a = g.nextToken()
        val b = g.nextToken()
        val c = g.nextToken()
        assertEquals(1L, a)
        assertEquals(2L, b)
        assertEquals(3L, c)
        assertEquals(3L, g.current())
    }

    @Test
    fun isCurrent_onlyLatestWins() {
        val g = WidgetUpdateGeneration()
        val older = g.nextToken()
        val newer = g.nextToken()
        assertFalse(g.isCurrent(older))
        assertTrue(g.isCurrent(newer))
    }

    @Test
    fun supersededWhileWaiting_skipsOlder() {
        // Simulate single-flight waiters: A then B then C; only C should apply.
        val g = WidgetUpdateGeneration()
        val a = g.nextToken()
        val b = g.nextToken()
        val c = g.nextToken()
        assertFalse("A superseded", g.isCurrent(a))
        assertFalse("B superseded", g.isCurrent(b))
        assertTrue("C is latest", g.isCurrent(c))
    }
}
