package com.example.workhourstracker.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * All week math lives here. The work week runs Wednesday through Tuesday.
 */
object WeekUtils {

    /** The Wednesday on/before [date] — the start of the work week containing [date]. */
    fun weekStartFor(date: LocalDate): LocalDate {
        val diff = (date.dayOfWeek.value - DayOfWeek.WEDNESDAY.value + 7) % 7
        return date.minusDays(diff.toLong())
    }

    fun weekEndFor(weekStart: LocalDate): LocalDate = weekStart.plusDays(6)

    fun previousWeekStart(weekStart: LocalDate): LocalDate = weekStart.minusDays(7)

    /** The 7 dates (Wed..Tue) belonging to the week that starts on [weekStart]. */
    fun daysInWeek(weekStart: LocalDate): List<LocalDate> = (0..6).map { weekStart.plusDays(it.toLong()) }

    /** Next occurrence of Wednesday at 2:00 AM, strictly after [from]. */
    fun nextWednesday2AM(from: LocalDateTime = LocalDateTime.now()): LocalDateTime {
        val candidateDate = from.toLocalDate().let { date ->
            val diff = (DayOfWeek.WEDNESDAY.value - date.dayOfWeek.value + 7) % 7
            date.plusDays(diff.toLong())
        }
        var candidate = candidateDate.atTime(2, 0)
        if (!candidate.isAfter(from)) {
            candidate = candidate.plusWeeks(1)
        }
        return candidate
    }

    fun epochMillis(dateTime: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()
}
