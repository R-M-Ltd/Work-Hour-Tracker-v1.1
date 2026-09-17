package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class WeekUtilsTest {

    @Test
    fun weekStartFor_wednesdayDefault() {
        // 2026-09-17 is Thursday → week start Wed 2026-09-16
        val thu = LocalDate.of(2026, 9, 17)
        assertEquals(LocalDate.of(2026, 9, 16), WeekUtils.weekStartFor(thu, DayOfWeek.WEDNESDAY))
        assertEquals(LocalDate.of(2026, 9, 16), WeekUtils.weekStartFor(LocalDate.of(2026, 9, 16), DayOfWeek.WEDNESDAY))
    }

    @Test
    fun weekStartFor_sunday() {
        val thu = LocalDate.of(2026, 9, 17)
        assertEquals(LocalDate.of(2026, 9, 13), WeekUtils.weekStartFor(thu, DayOfWeek.SUNDAY))
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
    }
}
