package com.rmltd.workhourstracker.widget

import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic generation tokens for coalescing overlapping widget refreshes.
 *
 * Callers take [nextToken] when enqueueing work. After acquiring the updater
 * mutex **and** after the Room/prefs load, apply RemoteViews only if
 * [isCurrent] — so a superseded waiter that finished loading still drops its
 * stale snapshot. Older completes are ignored so the latest snapshot wins.
 */
class WidgetUpdateGeneration {
    private val counter = AtomicLong(0L)

    fun nextToken(): Long = counter.incrementAndGet()

    fun current(): Long = counter.get()

    fun isCurrent(token: Long): Boolean = token == counter.get()
}

/**
 * Pure coalesce helper for tests: skip if superseded before load, load, then
 * skip again if superseded before apply. Models the updater gate without Android.
 */
internal inline fun <T> runIfCurrentGeneration(
    generation: WidgetUpdateGeneration,
    token: Long,
    load: () -> T,
    apply: (T) -> Unit
) {
    if (!generation.isCurrent(token)) return
    val data = load()
    if (!generation.isCurrent(token)) return
    apply(data)
}
