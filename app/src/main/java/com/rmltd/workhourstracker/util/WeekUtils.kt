package com.rmltd.workhourstracker.util

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * All week math lives here. The work week start day is configurable
 * (default Wednesday) via [weekStartDay].
 */
object WeekUtils {

    /** The week-start day on/before [date] for the week containing [date]. */
    fun weekStartFor(
        date: LocalDate,
        weekStartDay: DayOfWeek = DayOfWeek.WEDNESDAY
    ): LocalDate {
        val diff = (date.dayOfWeek.value - weekStartDay.value + 7) % 7
        return date.minusDays(diff.toLong())
    }

    fun weekEndFor(weekStart: LocalDate): LocalDate = weekStart.plusDays(6)

    fun previousWeekStart(weekStart: LocalDate): LocalDate = weekStart.minusDays(7)

    /** The 7 dates belonging to the week that starts on [weekStart]. */
    fun daysInWeek(weekStart: LocalDate): List<LocalDate> =
        (0..6).map { weekStart.plusDays(it.toLong()) }

    /**
     * Next occurrence of the configured week-start day at 2:00 AM,
     * strictly after [from]. Used for the weekly archive/reset alarm.
     */
    fun nextWeekStart2AM(
        from: LocalDateTime = LocalDateTime.now(),
        weekStartDay: DayOfWeek = DayOfWeek.WEDNESDAY
    ): LocalDateTime {
        val candidateDate = from.toLocalDate().let { date ->
            val diff = (weekStartDay.value - date.dayOfWeek.value + 7) % 7
            date.plusDays(diff.toLong())
        }
        var candidate = candidateDate.atTime(2, 0)
        if (!candidate.isAfter(from)) {
            candidate = candidate.plusWeeks(1)
        }
        return candidate
    }

    /** @deprecated Prefer [nextWeekStart2AM]; kept for call-site clarity during migration. */
    fun nextWednesday2AM(from: LocalDateTime = LocalDateTime.now()): LocalDateTime =
        nextWeekStart2AM(from, DayOfWeek.WEDNESDAY)

    fun epochMillis(dateTime: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    fun weekEndDayName(weekStartDay: DayOfWeek): DayOfWeek =
        DayOfWeek.of(((weekStartDay.value + 5) % 7) + 1)

    /**
     * Safe epoch-day → [LocalDate]. Extreme / invalid values fall back to today (L5).
     * [LocalDate.ofEpochDay] throws [DateTimeException] outside supported range.
     */
    fun dateFromEpochDayOrToday(epochDay: Long?, today: LocalDate = LocalDate.now()): LocalDate {
        if (epochDay == null) return today
        return try {
            LocalDate.ofEpochDay(epochDay)
        } catch (_: DateTimeException) {
            today
        } catch (_: ArithmeticException) {
            today
        }
    }
}
