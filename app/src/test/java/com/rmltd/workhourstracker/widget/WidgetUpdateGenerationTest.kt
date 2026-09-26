package com.rmltd.workhourstracker.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Generation coalescing beyond counter-only: post-load gate skips stale apply.
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

    @Test
    fun postLoadCheck_dropsStaleSnapshotAfterSupersede() {
        // Models updater: check → load → check again → apply.
        val g = WidgetUpdateGeneration()
        val older = g.nextToken()
        var applied: String? = null
        // Newer request arrives while older is "loading"
        val newer = g.nextToken()
        runIfCurrentGeneration(
            generation = g,
            token = older,
            load = { "stale-room" },
            apply = { applied = it }
        )
        assertEquals(null, applied)

        runIfCurrentGeneration(
            generation = g,
            token = newer,
            load = { "fresh-room" },
            apply = { applied = it }
        )
        assertEquals("fresh-room", applied)
    }

    @Test
    fun postLoadCheck_appliesWhenStillCurrent() {
        val g = WidgetUpdateGeneration()
        val token = g.nextToken()
        var applied: String? = null
        runIfCurrentGeneration(
            generation = g,
            token = token,
            load = { "ok" },
            apply = { applied = it }
        )
        assertEquals("ok", applied)
    }

    @Test
    fun coalesce_threeOverlapping_onlyLatestApplies() {
        val g = WidgetUpdateGeneration()
        val a = g.nextToken()
        val b = g.nextToken()
        val c = g.nextToken()
        val applied = mutableListOf<Long>()
        for (token in listOf(a, b, c)) {
            runIfCurrentGeneration(
                generation = g,
                token = token,
                load = { token },
                apply = { applied.add(it) }
            )
        }
        assertEquals(listOf(c), applied)
    }
}
