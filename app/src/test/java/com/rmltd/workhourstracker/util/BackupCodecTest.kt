package com.rmltd.workhourstracker.util

import com.rmltd.workhourstracker.data.DailyEntry
import com.rmltd.workhourstracker.data.WeekLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupCodecTest {

    private val prefs = BackupCodec.PrefsSnapshot(
        weekStartDay = 3,
        weeklyGoalHours = 40.0,
        hourlyRate = 25.5,
        colorTheme = "blue",
        fontStyle = "serif",
        reminderEnabled = true,
        reminderHour = 18,
        reminderMinute = 30,
        endOfDayEnabled = true,
        endOfDayHour = 20,
        endOfDayMinute = 0
    )

    private fun entry(day: Long, comments: String = "note") = DailyEntry(
        dateEpochDay = day,
        hoursWorked = 8.0,
        comments = comments,
        weekStartEpochDay = day,
        updatedAtEpochMillis = 1_000L,
        clockInMinutes = 9 * 60,
        clockOutMinutes = 17 * 60,
        lunchOutMinutes = 12 * 60,
        lunchInMinutes = 12 * 60 + 30,
        breakDurationMinutes = null,
        breakPaid = false
    )

    @Test
    fun roundTrip_preservesEntriesPrefsAndWeekLogs() {
        val entries = listOf(entry(20_000), entry(20_001, comments = "hello, \"world\""))
        val weeks = listOf(
            WeekLog(
                weekStartEpochDay = 20_000,
                weekEndEpochDay = 20_006,
                totalHours = 16.0,
                archivedAtEpochMillis = 2_000L
            )
        )
        val json = BackupCodec.encode(
            prefs = prefs,
            entries = entries,
            weekLogs = weeks,
            appVersion = "1.3.15",
            exportedAtEpochMillis = 99L
        )
        val decoded = BackupCodec.decode(json)
        assertEquals(1, decoded.formatVersion)
        assertEquals("1.3.15", decoded.appVersion)
        assertEquals(99L, decoded.exportedAtEpochMillis)
        assertEquals(prefs, decoded.prefs)
        assertEquals(2, decoded.entries.size)
        assertEquals("hello, \"world\"", decoded.entries[1].comments)
        assertEquals(9 * 60, decoded.entries[0].clockInMinutes)
        assertEquals(12 * 60 + 30, decoded.entries[0].lunchInMinutes)
        assertEquals(1, decoded.weekLogs.size)
        assertEquals(16.0, decoded.weekLogs[0].totalHours, 0.001)
    }

    @Test
    fun roundTrip_nullClocksAndBreakPaid() {
        val e = DailyEntry(
            dateEpochDay = 1,
            hoursWorked = 0.0,
            comments = "",
            weekStartEpochDay = 1,
            clockInMinutes = 540,
            clockOutMinutes = null,
            breakDurationMinutes = 30,
            breakPaid = true
        )
        val json = BackupCodec.encode(prefs, listOf(e), emptyList(), "1.3.15", 1L)
        val decoded = BackupCodec.decode(json)
        assertEquals(540, decoded.entries[0].clockInMinutes)
        assertEquals(null, decoded.entries[0].clockOutMinutes)
        assertEquals(30, decoded.entries[0].breakDurationMinutes)
        assertTrue(decoded.entries[0].breakPaid)
    }

    @Test
    fun decode_rejectsEmpty() {
        try {
            BackupCodec.decode("  ")
            fail("expected")
        } catch (e: BackupCodec.BackupValidationException) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun decode_rejectsBadJson() {
        try {
            BackupCodec.decode("{not json")
            fail("expected")
        } catch (e: BackupCodec.BackupValidationException) {
            assertTrue(e.message!!.contains("JSON"))
        }
    }

    @Test
    fun decode_rejectsWrongVersion() {
        val json = """{"formatVersion":99,"prefs":{"weekStartDay":3},"entries":[]}"""
        try {
            BackupCodec.decode(json)
            fail("expected")
        } catch (e: BackupCodec.BackupValidationException) {
            assertTrue(e.message!!.contains("formatVersion"))
        }
    }

    @Test
    fun decode_rejectsDuplicateDates() {
        val base = BackupCodec.encode(prefs, listOf(entry(5)), emptyList(), "1.3.15", 1L)
        // Inject a duplicate by decoding then re-encoding is hard; hand-build minimal bad JSON
        val bad = """
            {"formatVersion":1,"prefs":{"weekStartDay":3,"weeklyGoalHours":40.0,"hourlyRate":0.0,
            "colorTheme":"purple","fontStyle":"default","reminderEnabled":true,"reminderHour":18,
            "reminderMinute":0,"endOfDayEnabled":true,"endOfDayHour":20,"endOfDayMinute":0},
            "entries":[{"dateEpochDay":5,"hoursWorked":1.0,"comments":"","weekStartEpochDay":5},
            {"dateEpochDay":5,"hoursWorked":2.0,"comments":"","weekStartEpochDay":5}],
            "weekLogs":[]}
        """.trimIndent()
        try {
            BackupCodec.decode(bad)
            fail("expected")
        } catch (e: BackupCodec.BackupValidationException) {
            assertTrue(e.message!!.contains("Duplicate"))
        }
        assertFalse(base.isEmpty())
    }

    @Test
    fun encode_includesFormatVersionKey() {
        val json = BackupCodec.encode(prefs, emptyList(), emptyList(), "1.3.15", 1L)
        assertTrue(json.contains("\"formatVersion\""))
        assertTrue(json.contains("\"entries\""))
        assertTrue(json.contains("\"prefs\""))
    }
}
