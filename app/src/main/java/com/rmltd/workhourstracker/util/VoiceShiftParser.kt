package com.rmltd.workhourstracker.util

import java.util.Locale

/**
 * Parses a single spoken utterance into clock-in / lunch / clock-out minutes.
 *
 * Examples:
 *  - "clocked in at 7:30, lunch 12 to 12:30, out at 4"
 *  - "in at 8, out at 5"
 *  - "started 7:45 lunch from 12 to 12:30 finished at 4:15 pm"
 */
object VoiceShiftParser {

    data class ShiftTimes(
        val clockIn: Int? = null,
        val clockOut: Int? = null,
        val lunchOut: Int? = null,
        val lunchIn: Int? = null
    ) {
        val filledCount: Int
            get() = listOfNotNull(clockIn, clockOut, lunchOut, lunchIn).size

        val hasAny: Boolean get() = filledCount > 0
    }

    fun parse(spoken: String): ShiftTimes {
        val s = spoken.lowercase(Locale.getDefault())
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()

        var clockIn: Int? = null
        var clockOut: Int? = null
        var lunchOut: Int? = null
        var lunchIn: Int? = null

        // Lunch range first (two times), so those times are not also grabbed as in/out.
        val lunchRange = Regex(
            """(?:lunch|broke|break)\s*(?:from\s+)?(.+?)\s+(?:to|until|till|-|through)\s+(.+?)(?=,|\band\b|clock|out|in\b|finished|left|ended|$)"""
        ).find(s)
        if (lunchRange != null) {
            lunchOut = extractClockMinutes(lunchRange.groupValues[1])
            lunchIn = extractClockMinutes(lunchRange.groupValues[2])
        } else {
            // "lunch at 12" / "lunch 12:30" — single lunch-out hint only
            val lunchSingle = Regex("""(?:lunch|broke|break)\s*(?:at\s+|around\s+)?(.+?)(?=,|\band\b|clock|out|in\b|finished|left|$)""")
                .find(s)
            if (lunchSingle != null) {
                lunchOut = extractClockMinutes(lunchSingle.groupValues[1])
            }
        }

        clockIn = extractLabeled(
            s,
            listOf(
                Regex("""(?:clocked\s+in|clock\s+in|started|start(?:ed)?|in)\s*(?:at\s+|around\s+)?(.+?)(?=,|\band\b|lunch|break|out|finished|left|ended|$)""")
            )
        ) ?: clockIn

        clockOut = extractLabeled(
            s,
            listOf(
                Regex("""(?:clocked\s+out|clock\s+out|finished|ended|left|out)\s*(?:at\s+|around\s+)?(.+?)(?=,|\band\b|lunch|break|in\b|$)""")
            )
        ) ?: clockOut

        // Fallback: unlabeled times in order → in, [lunch out, lunch in], out
        if (clockIn == null || clockOut == null) {
            val times = extractAllTimes(s)
            when {
                times.size >= 4 -> {
                    if (clockIn == null) clockIn = times[0]
                    if (lunchOut == null) lunchOut = times[1]
                    if (lunchIn == null) lunchIn = times[2]
                    if (clockOut == null) clockOut = times[3]
                }
                times.size == 3 -> {
                    if (clockIn == null) clockIn = times[0]
                    if (lunchOut == null && lunchIn == null) {
                        lunchOut = times[1]
                    } else if (lunchOut == null) {
                        lunchOut = times[1]
                    }
                    if (clockOut == null) clockOut = times[2]
                }
                times.size == 2 -> {
                    if (clockIn == null) clockIn = times[0]
                    if (clockOut == null) clockOut = times[1]
                }
                times.size == 1 -> {
                    if (clockIn == null && clockOut == null) clockIn = times[0]
                }
            }
        }

        return disambiguateDayShift(ShiftTimes(clockIn, clockOut, lunchOut, lunchIn))
    }

    /**
     * If am/pm was omitted, a bare "out at 4" after a morning clock-in would land
     * at 4:00 AM. For day shifts (clock-in before noon), bump times that parsed
     * at-or-before clock-in into the afternoon.
     */
    private fun disambiguateDayShift(times: ShiftTimes): ShiftTimes {
        val cin = times.clockIn ?: return times
        if (cin >= 12 * 60) return times

        fun bump(t: Int?): Int? {
            if (t == null) return null
            val hour = t / 60
            return if (hour in 1..11 && t <= cin) t + 12 * 60 else t
        }

        var lunchOut = bump(times.lunchOut)
        var lunchIn = bump(times.lunchIn)
        // Ensure lunch-in is after lunch-out when both present
        if (lunchOut != null && lunchIn != null && lunchIn <= lunchOut) {
            val hour = lunchIn / 60
            if (hour in 1..11) lunchIn = lunchIn + 12 * 60
        }
        return times.copy(
            clockOut = bump(times.clockOut),
            lunchOut = lunchOut,
            lunchIn = lunchIn
        )
    }

    private fun extractLabeled(spoken: String, patterns: List<Regex>): Int? {
        for (pattern in patterns) {
            val m = pattern.find(spoken) ?: continue
            val parsed = extractClockMinutes(m.groupValues[1])
            if (parsed != null) return parsed
        }
        return null
    }

    /** Pull every recognizable clock time from [spoken], left-to-right. */
    fun extractAllTimes(spoken: String): List<Int> {
        val s = spoken.lowercase(Locale.getDefault())
        val results = mutableListOf<Pair<Int, Int>>() // startIndex to minutes

        val patterns = listOf(
            Regex("""\b(\d{1,2})[:\s](\d{2})\s*([ap]\.?m\.?)?\b"""),
            Regex("""\b(\d{1,2})(\d{2})\s*([ap]\.?m\.?)?\b"""),
            Regex("""\b(\d{1,2})\s*([ap]\.?m\.?)\b""")
        )
        for (pattern in patterns) {
            for (m in pattern.findAll(s)) {
                val parsed = extractClockMinutes(m.value) ?: continue
                // Avoid overlapping matches (e.g. compact vs hour+am)
                if (results.any { it.first == m.range.first }) continue
                if (results.any { overlap(it.first, m.range.first, m.range.last) }) continue
                results.add(m.range.first to parsed)
            }
        }
        return results.sortedBy { it.first }.map { it.second }.distinct()
    }

    private fun overlap(existingStart: Int, start: Int, end: Int): Boolean =
        start <= existingStart && existingStart <= end
}

/**
 * "7:30 am", "7 30 pm", "19:15", "730am", "4" → minutes from midnight.
 * Shared by per-field mic and whole-shift parsing.
 */
fun extractClockMinutes(spoken: String): Int? {
    val s = spoken.lowercase(Locale.getDefault())
    val pm = Regex("""(?:^|\s|\d)p\.?m\.?\b""").containsMatchIn(s)
    val am = Regex("""(?:^|\s|\d)a\.?m\.?\b""").containsMatchIn(s)
    val hm = Regex("""(\d{1,2})[:\s](\d{2})""").find(s)
    val compact = Regex("""\b(\d{1,2})(\d{2})(?=\s*(?:[ap]\.?m\.?)?\b)""").find(s)
    val hourOnly = Regex("""\b(\d{1,2})\b""").find(s)
    val hour: Int
    val minute: Int
    when {
        hm != null -> {
            hour = hm.groupValues[1].toInt()
            minute = hm.groupValues[2].toInt()
        }
        compact != null -> {
            hour = compact.groupValues[1].toInt()
            minute = compact.groupValues[2].toInt()
        }
        hourOnly != null -> {
            hour = hourOnly.groupValues[1].toInt()
            minute = 0
        }
        else -> return null
    }
    if (minute !in 0..59) return null
    var h24 = hour
    when {
        pm && hour in 1..11 -> h24 = hour + 12
        am && hour == 12 -> h24 = 0
        hour == 12 && pm -> h24 = 12
        !am && !pm && hour in 0..23 -> h24 = hour
        am && hour in 1..11 -> h24 = hour
        hour !in 0..23 -> return null
    }
    if (h24 !in 0..23) return null
    return h24 * 60 + minute
}
