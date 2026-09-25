package com.rmltd.workhourstracker.util

import com.rmltd.workhourstracker.data.DailyEntry
import com.rmltd.workhourstracker.data.WeekLog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON encode/decode for a single backup file (phone swap / restore).
 * No Android Context — unit-testable on the JVM.
 *
 * Included:
 * - All [DailyEntry] rows (clocks, hours, notes/comments, break fields)
 * - [WeekLog] archive summaries
 * - Essential prefs: week start, weekly goal, hourly rate, color theme, font style,
 *   daily reminder on/time, end-of-day reminder on/time
 *
 * Not included: transient EOD fired/snooze stamps, system notification permission.
 */
object BackupCodec {

    const val FORMAT_VERSION = 1
    const val MIME_TYPE = "application/json"
    const val FILE_NAME = "work_hours_backup.json"

    data class PrefsSnapshot(
        val weekStartDay: Int,
        val weeklyGoalHours: Double,
        val hourlyRate: Double,
        val colorTheme: String,
        val fontStyle: String,
        val reminderEnabled: Boolean,
        val reminderHour: Int,
        val reminderMinute: Int,
        val endOfDayEnabled: Boolean,
        val endOfDayHour: Int,
        val endOfDayMinute: Int
    )

    data class BackupPayload(
        val formatVersion: Int,
        val appVersion: String,
        val exportedAtEpochMillis: Long,
        val prefs: PrefsSnapshot,
        val entries: List<DailyEntry>,
        val weekLogs: List<WeekLog>
    )

    class BackupValidationException(message: String) : Exception(message)

    fun encode(
        prefs: PrefsSnapshot,
        entries: List<DailyEntry>,
        weekLogs: List<WeekLog>,
        appVersion: String,
        exportedAtEpochMillis: Long = System.currentTimeMillis()
    ): String {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("appVersion", appVersion)
        root.put("exportedAtEpochMillis", exportedAtEpochMillis)
        root.put("prefs", prefsToJson(prefs))
        val entriesArr = JSONArray()
        for (e in entries.sortedBy { it.dateEpochDay }) {
            entriesArr.put(entryToJson(e))
        }
        root.put("entries", entriesArr)
        val weeksArr = JSONArray()
        for (w in weekLogs.sortedBy { it.weekStartEpochDay }) {
            weeksArr.put(weekLogToJson(w))
        }
        root.put("weekLogs", weeksArr)
        return root.toString(2)
    }

    fun decode(json: String): BackupPayload {
        val trimmed = json.trim()
        if (trimmed.isEmpty()) {
            throw BackupValidationException("Backup file is empty")
        }
        val root = try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            throw BackupValidationException("Backup is not valid JSON: ${e.message}")
        }
        val version = root.optInt("formatVersion", -1)
        if (version != FORMAT_VERSION) {
            throw BackupValidationException(
                "Unsupported backup formatVersion=$version (expected $FORMAT_VERSION)"
            )
        }
        if (!root.has("prefs") || !root.has("entries")) {
            throw BackupValidationException("Backup missing prefs or entries")
        }
        val prefs = prefsFromJson(root.getJSONObject("prefs"))
        val entriesArr = root.getJSONArray("entries")
        val entries = ArrayList<DailyEntry>(entriesArr.length())
        val seenDays = HashSet<Long>()
        for (i in 0 until entriesArr.length()) {
            val entry = entryFromJson(entriesArr.getJSONObject(i))
            if (!seenDays.add(entry.dateEpochDay)) {
                throw BackupValidationException(
                    "Duplicate entry for dateEpochDay=${entry.dateEpochDay}"
                )
            }
            entries.add(entry)
        }
        val weekLogs = ArrayList<WeekLog>()
        if (root.has("weekLogs")) {
            val weeksArr = root.getJSONArray("weekLogs")
            val seenWeeks = HashSet<Long>()
            for (i in 0 until weeksArr.length()) {
                val log = weekLogFromJson(weeksArr.getJSONObject(i))
                if (!seenWeeks.add(log.weekStartEpochDay)) {
                    throw BackupValidationException(
                        "Duplicate weekLog for weekStartEpochDay=${log.weekStartEpochDay}"
                    )
                }
                weekLogs.add(log)
            }
        }
        return BackupPayload(
            formatVersion = version,
            appVersion = root.optString("appVersion", ""),
            exportedAtEpochMillis = root.optLong("exportedAtEpochMillis", 0L),
            prefs = prefs,
            entries = entries,
            weekLogs = weekLogs
        )
    }

    private fun prefsToJson(p: PrefsSnapshot): JSONObject =
        JSONObject()
            .put("weekStartDay", p.weekStartDay)
            .put("weeklyGoalHours", p.weeklyGoalHours)
            .put("hourlyRate", p.hourlyRate)
            .put("colorTheme", p.colorTheme)
            .put("fontStyle", p.fontStyle)
            .put("reminderEnabled", p.reminderEnabled)
            .put("reminderHour", p.reminderHour)
            .put("reminderMinute", p.reminderMinute)
            .put("endOfDayEnabled", p.endOfDayEnabled)
            .put("endOfDayHour", p.endOfDayHour)
            .put("endOfDayMinute", p.endOfDayMinute)

    private fun prefsFromJson(o: JSONObject): PrefsSnapshot {
        val weekStart = o.optInt("weekStartDay", 3)
        if (weekStart !in 1..7) {
            throw BackupValidationException("Invalid weekStartDay=$weekStart")
        }
        return PrefsSnapshot(
            weekStartDay = weekStart,
            weeklyGoalHours = o.optDouble("weeklyGoalHours", 40.0).coerceIn(0.01, 168.0),
            hourlyRate = o.optDouble("hourlyRate", 0.0).coerceIn(0.0, 10_000.0),
            colorTheme = o.optString("colorTheme", "purple"),
            fontStyle = o.optString("fontStyle", "default"),
            reminderEnabled = o.optBoolean("reminderEnabled", true),
            reminderHour = o.optInt("reminderHour", 18).coerceIn(0, 23),
            reminderMinute = o.optInt("reminderMinute", 0).coerceIn(0, 59),
            endOfDayEnabled = o.optBoolean("endOfDayEnabled", true),
            endOfDayHour = o.optInt("endOfDayHour", 20).coerceIn(0, 23),
            endOfDayMinute = o.optInt("endOfDayMinute", 0).coerceIn(0, 59)
        )
    }

    private fun entryToJson(e: DailyEntry): JSONObject {
        val o = JSONObject()
        o.put("dateEpochDay", e.dateEpochDay)
        o.put("hoursWorked", e.hoursWorked)
        o.put("comments", e.comments)
        o.put("weekStartEpochDay", e.weekStartEpochDay)
        o.put("updatedAtEpochMillis", e.updatedAtEpochMillis)
        putNullableInt(o, "clockInMinutes", e.clockInMinutes)
        putNullableInt(o, "clockOutMinutes", e.clockOutMinutes)
        putNullableInt(o, "lunchOutMinutes", e.lunchOutMinutes)
        putNullableInt(o, "lunchInMinutes", e.lunchInMinutes)
        putNullableInt(o, "breakDurationMinutes", e.breakDurationMinutes)
        o.put("breakPaid", e.breakPaid)
        return o
    }

    private fun entryFromJson(o: JSONObject): DailyEntry {
        if (!o.has("dateEpochDay")) {
            throw BackupValidationException("Entry missing dateEpochDay")
        }
        val dateEpoch = o.getLong("dateEpochDay")
        return DailyEntry(
            dateEpochDay = dateEpoch,
            hoursWorked = o.optDouble("hoursWorked", 0.0),
            comments = o.optString("comments", ""),
            weekStartEpochDay = o.optLong("weekStartEpochDay", dateEpoch),
            updatedAtEpochMillis = o.optLong("updatedAtEpochMillis", 0L),
            clockInMinutes = nullableInt(o, "clockInMinutes"),
            clockOutMinutes = nullableInt(o, "clockOutMinutes"),
            lunchOutMinutes = nullableInt(o, "lunchOutMinutes"),
            lunchInMinutes = nullableInt(o, "lunchInMinutes"),
            breakDurationMinutes = nullableInt(o, "breakDurationMinutes"),
            breakPaid = o.optBoolean("breakPaid", false)
        )
    }

    private fun weekLogToJson(w: WeekLog): JSONObject =
        JSONObject()
            .put("weekStartEpochDay", w.weekStartEpochDay)
            .put("weekEndEpochDay", w.weekEndEpochDay)
            .put("totalHours", w.totalHours)
            .put("archivedAtEpochMillis", w.archivedAtEpochMillis)

    private fun weekLogFromJson(o: JSONObject): WeekLog {
        if (!o.has("weekStartEpochDay") || !o.has("weekEndEpochDay")) {
            throw BackupValidationException("weekLog missing start/end")
        }
        return WeekLog(
            weekStartEpochDay = o.getLong("weekStartEpochDay"),
            weekEndEpochDay = o.getLong("weekEndEpochDay"),
            totalHours = o.optDouble("totalHours", 0.0),
            archivedAtEpochMillis = o.optLong("archivedAtEpochMillis", 0L)
        )
    }

    private fun putNullableInt(o: JSONObject, key: String, value: Int?) {
        if (value == null) o.put(key, JSONObject.NULL) else o.put(key, value)
    }

    private fun nullableInt(o: JSONObject, key: String): Int? {
        if (!o.has(key) || o.isNull(key)) return null
        return o.getInt(key)
    }
}
