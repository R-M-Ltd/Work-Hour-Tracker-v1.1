package com.rmltd.workhourstracker.util

import com.rmltd.workhourstracker.data.DailyEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pure JVM coverage for [CsvExporter.buildCsv] (no Context / FileProvider).
 * Clock columns use Locale.US ("h:mm a") for device-stable exports.
 */
class CsvExporterTest {

    private val weekStart = LocalDate.of(2026, 9, 16) // Wed
    private val header =
        "week_start,date,clock_in,clock_out,lunch_out,lunch_in,hours,comments"

    private fun entry(
        date: LocalDate,
        clockIn: Int? = 9 * 60,
        clockOut: Int? = 17 * 60,
        lunchOut: Int? = null,
        lunchIn: Int? = null,
        hours: Double = 8.0,
        comments: String = ""
    ) = DailyEntry(
        dateEpochDay = date.toEpochDay(),
        hoursWorked = hours,
        comments = comments,
        weekStartEpochDay = weekStart.toEpochDay(),
        clockInMinutes = clockIn,
        clockOutMinutes = clockOut,
        lunchOutMinutes = lunchOut,
        lunchInMinutes = lunchIn
    )

    @Test
    fun headerOnly_whenNoWeeks() {
        assertEquals("$header\n", CsvExporter.buildCsv(emptyList()))
    }

    @Test
    fun row_formatsClocksHoursAndEmptyLunch() {
        val day = LocalDate.of(2026, 9, 17)
        val csv = CsvExporter.buildCsv(
            listOf(weekStart to listOf(entry(day, hours = 8.5)))
        )
        assertEquals(
            "$header\n" +
                "2026-09-16,2026-09-17,9:00 AM,5:00 PM,,,8.50,\n",
            csv
        )
    }

    @Test
    fun row_includesLunchAndCommentsPlain() {
        val day = LocalDate.of(2026, 9, 17)
        val csv = CsvExporter.buildCsv(
            listOf(
                weekStart to listOf(
                    entry(
                        day,
                        lunchOut = 12 * 60,
                        lunchIn = 12 * 60 + 30,
                        hours = 7.5,
                        comments = "desk work"
                    )
                )
            )
        )
        assertEquals(
            "$header\n" +
                "2026-09-16,2026-09-17,9:00 AM,5:00 PM,12:00 PM,12:30 PM,7.50,desk work\n",
            csv
        )
    }

    @Test
    fun escaping_commaQuoteAndNewline() {
        val day = LocalDate.of(2026, 9, 17)
        val csv = CsvExporter.buildCsv(
            listOf(
                weekStart to listOf(
                    entry(day, comments = "hello, \"world\"\nnext"),
                    entry(day.plusDays(1), comments = "plain")
                )
            )
        )
        assertEquals(
            "$header\n" +
                "2026-09-16,2026-09-17,9:00 AM,5:00 PM,,,8.00,\"hello, \"\"world\"\"\nnext\"\n" +
                "2026-09-16,2026-09-18,9:00 AM,5:00 PM,,,8.00,plain\n",
            csv
        )
    }

    @Test
    fun escaping_carriageReturnNeedsQuotes() {
        val day = LocalDate.of(2026, 9, 17)
        val csv = CsvExporter.buildCsv(
            listOf(weekStart to listOf(entry(day, comments = "a\rb")))
        )
        assertEquals(
            "$header\n" +
                "2026-09-16,2026-09-17,9:00 AM,5:00 PM,,,8.00,\"a\rb\"\n",
            csv
        )
    }

    @Test
    fun openShift_blankOutAndZeroHours() {
        val day = LocalDate.of(2026, 9, 17)
        val csv = CsvExporter.buildCsv(
            listOf(
                weekStart to listOf(
                    entry(day, clockIn = 22 * 60, clockOut = null, hours = 0.0)
                )
            )
        )
        assertEquals(
            "$header\n" +
                "2026-09-16,2026-09-17,10:00 PM,,,,0.00,\n",
            csv
        )
    }

    @Test
    fun sortsEntriesByDateWithinWeek() {
        val wed = LocalDate.of(2026, 9, 16)
        val fri = LocalDate.of(2026, 9, 18)
        val thu = LocalDate.of(2026, 9, 17)
        val csv = CsvExporter.buildCsv(
            listOf(weekStart to listOf(entry(fri), entry(wed), entry(thu)))
        )
        assertEquals(
            "$header\n" +
                "2026-09-16,2026-09-16,9:00 AM,5:00 PM,,,8.00,\n" +
                "2026-09-16,2026-09-17,9:00 AM,5:00 PM,,,8.00,\n" +
                "2026-09-16,2026-09-18,9:00 AM,5:00 PM,,,8.00,\n",
            csv
        )
    }

    @Test
    fun multipleWeeks_preserveCallerOrder() {
        val w1 = LocalDate.of(2026, 9, 9)
        val w2 = LocalDate.of(2026, 9, 16)
        val csv = CsvExporter.buildCsv(
            listOf(
                w1 to listOf(entry(LocalDate.of(2026, 9, 10))),
                w2 to listOf(entry(LocalDate.of(2026, 9, 17)))
            )
        )
        assertEquals(
            "$header\n" +
                "2026-09-09,2026-09-10,9:00 AM,5:00 PM,,,8.00,\n" +
                "2026-09-16,2026-09-17,9:00 AM,5:00 PM,,,8.00,\n",
            csv
        )
    }

    @Test
    fun emptyComment_noQuotes() {
        val day = LocalDate.of(2026, 9, 17)
        val csv = CsvExporter.buildCsv(listOf(weekStart to listOf(entry(day))))
        assertFalse(csv.contains('"'))
        assertTrue(csv.trimEnd().endsWith(','))
    }
}
