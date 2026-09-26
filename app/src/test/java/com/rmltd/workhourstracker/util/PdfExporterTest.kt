package com.rmltd.workhourstracker.util

import com.rmltd.workhourstracker.data.DailyEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure JVM coverage for [PdfExporter] row / total / range helpers (no PdfDocument).
 */
class PdfExporterTest {

    private val weekStart = LocalDate.of(2026, 9, 16)

    private fun entry(
        date: LocalDate,
        clockIn: Int? = 9 * 60,
        clockOut: Int? = 17 * 60,
        hours: Double = 8.0
    ) = DailyEntry(
        dateEpochDay = date.toEpochDay(),
        hoursWorked = hours,
        weekStartEpochDay = weekStart.toEpochDay(),
        clockInMinutes = clockIn,
        clockOutMinutes = clockOut
    )

    @Test
    fun buildRows_empty() {
        assertTrue(PdfExporter.buildRows(emptyList()).isEmpty())
        assertEquals(0.0, PdfExporter.totalHours(emptyList()), 0.0)
    }

    @Test
    fun buildRows_sortedWithHoursAndRange() {
        val d1 = LocalDate.of(2026, 9, 17)
        val d2 = LocalDate.of(2026, 9, 18)
        val rows = PdfExporter.buildRows(
            listOf(
                weekStart to listOf(
                    entry(d2, hours = 7.5),
                    entry(d1, hours = 8.0)
                )
            )
        )
        assertEquals(2, rows.size)
        assertEquals("2026-09-17", rows[0].dateIso)
        assertEquals("2026-09-18", rows[1].dateIso)
        assertEquals(8.0, rows[0].hours, 0.0)
        assertEquals(15.5, PdfExporter.totalHours(rows), 0.0)
        assertTrue(rows[0].clockRange.contains("9:00"))
    }

    @Test
    fun rangeLabel_usesBoundsWhenProvided() {
        val start = LocalDate.of(2026, 9, 16)
        val end = LocalDate.of(2026, 9, 22)
        assertEquals(
            "2026-09-16 → 2026-09-22",
            PdfExporter.rangeLabel(emptyList(), start, end)
        )
    }

    @Test
    fun rangeLabel_fromRowsWhenNoBounds() {
        val d = LocalDate.of(2026, 9, 17)
        val rows = PdfExporter.buildRows(listOf(weekStart to listOf(entry(d))))
        assertEquals("2026-09-17 → 2026-09-17", PdfExporter.rangeLabel(rows))
    }
}
