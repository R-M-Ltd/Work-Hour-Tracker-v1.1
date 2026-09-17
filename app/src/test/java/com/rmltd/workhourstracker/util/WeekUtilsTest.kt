package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class WeekUtilsTest {

    // Thursday 2026-09-17 is a convenient anchor for several start-day checks.
    private val thu = LocalDate.of(2026, 9, 17)

    @Test
    fun weekStartFor_wednesdayDefault() {
        // 2026-09-17 is Thursday → week start Wed 2026-09-16
        assertEquals(LocalDate.of(2026, 9, 16), WeekUtils.weekStartFor(thu, DayOfWeek.WEDNESDAY))
        assertEquals(LocalDate.of(2026, 9, 16), WeekUtils.weekStartFor(LocalDate.of(2026, 9, 16), DayOfWeek.WEDNESDAY))
    }

    @Test
    fun weekStartFor_sunday() {
        assertEquals(LocalDate.of(2026, 9, 13), WeekUtils.weekStartFor(thu, DayOfWeek.SUNDAY))
    }

    @Test
    fun weekStartFor_monday() {
        assertEquals(LocalDate.of(2026, 9, 14), WeekUtils.weekStartFor(thu, DayOfWeek.MONDAY))
        // Monday itself is the start
        assertEquals(LocalDate.of(2026, 9, 14), WeekUtils.weekStartFor(LocalDate.of(2026, 9, 14), DayOfWeek.MONDAY))
    }

    @Test
    fun weekStartFor_friday() {
        assertEquals(LocalDate.of(2026, 9, 11), WeekUtils.weekStartFor(thu, DayOfWeek.FRIDAY))
        assertEquals(LocalDate.of(2026, 9, 18), WeekUtils.weekStartFor(LocalDate.of(2026, 9, 18), DayOfWeek.FRIDAY))
    }

    @Test
    fun weekStartFor_saturday() {
        assertEquals(LocalDate.of(2026, 9, 12), WeekUtils.weekStartFor(thu, DayOfWeek.SATURDAY))
    }

    @Test
    fun weekStartFor_tuesday() {
        assertEquals(LocalDate.of(2026, 9, 15), WeekUtils.weekStartFor(thu, DayOfWeek.TUESDAY))
    }

    @Test
    fun weekStartFor_allSevenDays_identityOnStartDay() {
        for (start in DayOfWeek.entries) {
            val onStart = WeekUtils.weekStartFor(thu, start)
            assertEquals(start, onStart.dayOfWeek)
            assertEquals(onStart, WeekUtils.weekStartFor(onStart, start))
            // End of that week is still in same week
            val end = onStart.plusDays(6)
            assertEquals(onStart, WeekUtils.weekStartFor(end, start))
        }
    }

    @Test
    fun weekEndFor_andPrevious() {
        val start = LocalDate.of(2026, 9, 16)
        assertEquals(LocalDate.of(2026, 9, 22), WeekUtils.weekEndFor(start))
        assertEquals(LocalDate.of(2026, 9, 9), WeekUtils.previousWeekStart(start))
    }

    @Test
    fun daysInWeek_sevenDates() {
        val start = LocalDate.of(2026, 9, 16)
        val days = WeekUtils.daysInWeek(start)
        assertEquals(7, days.size)
        assertEquals(start, days.first())
        assertEquals(start.plusDays(6), days.last())
    }

    @Test
    fun nextWeekStart2AM_strictlyAfter() {
        val from = LocalDateTime.of(2026, 9, 16, 2, 0) // exactly Wed 2 AM
        val next = WeekUtils.nextWeekStart2AM(from, DayOfWeek.WEDNESDAY)
        assertEquals(LocalDateTime.of(2026, 9, 23, 2, 0), next)
    }

    @Test
    fun nextWeekStart2AM_beforeTwoAmSameDay() {
        val from = LocalDateTime.of(2026, 9, 16, 1, 59)
        val next = WeekUtils.nextWeekStart2AM(from, DayOfWeek.WEDNESDAY)
        assertEquals(LocalDateTime.of(2026, 9, 16, 2, 0), next)
    }

    @Test
    fun nextWeekStart2AM_mondayConfigured() {
        val from = LocalDateTime.of(2026, 9, 17, 10, 0) // Thu
        val next = WeekUtils.nextWeekStart2AM(from, DayOfWeek.MONDAY)
        assertEquals(LocalDateTime.of(2026, 9, 21, 2, 0), next)
    }

    @Test
    fun dateFromEpochDayOrToday_valid() {
        val today = LocalDate.of(2026, 9, 17)
        val epoch = today.toEpochDay()
        assertEquals(today, WeekUtils.dateFromEpochDayOrToday(epoch, today))
    }

    @Test
    fun dateFromEpochDayOrToday_nullFallsBack() {
        val today = LocalDate.of(2026, 9, 17)
        assertEquals(today, WeekUtils.dateFromEpochDayOrToday(null, today))
    }

    @Test
    fun dateFromEpochDayOrToday_extremeFallsBack() {
        val today = LocalDate.of(2026, 9, 17)
        // Far beyond LocalDate supported range
        assertEquals(today, WeekUtils.dateFromEpochDayOrToday(Long.MAX_VALUE, today))
        assertEquals(today, WeekUtils.dateFromEpochDayOrToday(Long.MIN_VALUE, today))
    }

    @Test
    fun weekEndDayName() {
        assertEquals(DayOfWeek.TUESDAY, WeekUtils.weekEndDayName(DayOfWeek.WEDNESDAY))
        assertEquals(DayOfWeek.SATURDAY, WeekUtils.weekEndDayName(DayOfWeek.SUNDAY))
        assertEquals(DayOfWeek.SUNDAY, WeekUtils.weekEndDayName(DayOfWeek.MONDAY))
        assertEquals(DayOfWeek.THURSDAY, WeekUtils.weekEndDayName(DayOfWeek.FRIDAY))
    }

    @Test
    fun weekEndDayName_allSeven() {
        for (start in DayOfWeek.entries) {
            val end = WeekUtils.weekEndDayName(start)
            // End is always 6 calendar days after start → never the same weekday
            assertEquals(((start.value + 5) % 7) + 1, end.value)
            assertTrue(end != start)
        }
    }
}
