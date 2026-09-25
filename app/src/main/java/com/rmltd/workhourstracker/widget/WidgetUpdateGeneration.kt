package com.rmltd.workhourstracker.widget

import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic generation tokens for coalescing overlapping widget refreshes.
 * Callers take [nextToken] when starting work; after the Room read (or when
 * acquiring a serializing lock), apply RemoteViews only if [isCurrent].
 * Older completes are ignored so the latest snapshot always wins.
 */
class WidgetUpdateGeneration {
    private val counter = AtomicLong(0L)

    fun nextToken(): Long = counter.incrementAndGet()

    fun current(): Long = counter.get()

    fun isCurrent(token: Long): Boolean = token == counter.get()
}
