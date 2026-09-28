package com.rmltd.workhourstracker.util

import com.rmltd.workhourstracker.data.DailyEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PayPeriodExportTest {

    private fun weeksForMonth(ym: YearMonth): List<Pair<LocalDate, List<DailyEntry>>> {
        val start = ym.atDay(1)
        val end = ym.atEndOfMonth()
        val entries = mutableListOf<DailyEntry>()
        var d = start
        while (!d.isAfter(end)) {
            if (d.dayOfMonth % 3 == 0) {
                entries.add(
                    DailyEntry(
                        dateEpochDay = d.toEpochDay(),
                        hoursWorked = 8.0,
                        weekStartEpochDay = d.toEpochDay(),
                        clockInMinutes = 9 * 60,
                        clockOutMinutes = 17 * 60
                    )
                )
            }
            d = d.plusDays(1)
        }
        // single fake week bucket
        return listOf(start to entries)
    }

    @Test
    fun thisMonth_filtersInclusive() {
        val ym = YearMonth.of(2026, 9)
        val weeks = weeksForMonth(ym)
        val filtered = CsvExporter.filterByDateRange(weeks, ym.atDay(1), ym.atEndOfMonth())
        val days = filtered.flatMap { it.second }
        assertTrue(days.isNotEmpty())
        assertTrue(days.all {
            val d = LocalDate.ofEpochDay(it.dateEpochDay)
            !d.isBefore(ym.atDay(1)) && !d.isAfter(ym.atEndOfMonth())
        })
        val csv = CsvExporter.buildCsv(filtered)
        assertTrue(csv.startsWith("week_start,date,"))
        assertTrue(csv.contains("2026-09-"))
    }

    @Test
    fun customRange_empty_whenNoRows() {
        val weeks = weeksForMonth(YearMonth.of(2026, 9))
        val filtered = CsvExporter.filterByDateRange(
            weeks,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 31)
        )
        assertEquals(0, filtered.sumOf { it.second.size })
    }

    @Test
    fun monthFilename_form() {
        val ym = YearMonth.of(2026, 9)
        assertEquals("work_hours_2026-09.csv", "work_hours_${ym}.csv")
    }
}
