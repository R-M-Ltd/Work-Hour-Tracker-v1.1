package com.rmltd.workhourstracker.data.sync

import com.rmltd.workhourstracker.data.DailyEntry
import com.rmltd.workhourstracker.data.HoursSource
import org.json.JSONArray
import org.json.JSONObject

/** JSON snapshot for cloud sync file `work_hours_sync.json`. */
object CloudSyncCodec {

    const val SCHEMA_VERSION = 1
    const val FILENAME = "work_hours_sync.json"

    fun encode(
        entries: List<DailyEntry>,
        tombstones: Map<Long, Long>,
        deviceId: String = ""
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("deviceId", deviceId)
        root.put("exportedAtEpochMillis", System.currentTimeMillis())
        val arr = JSONArray()
        for (e in entries.sortedBy { it.dateEpochDay }) {
            arr.put(entryToJson(e))
        }
        root.put("entries", arr)
        val tombs = JSONObject()
        for ((k, v) in tombstones) {
            tombs.put(k.toString(), v)
        }
        root.put("tombstones", tombs)
        return root.toString()
    }

    data class Snapshot(
        val entries: List<DailyEntry>,
        val tombstones: Map<Long, Long>
    )

    fun decode(json: String): Snapshot {
        val root = JSONObject(json)
        val arr = root.optJSONArray("entries") ?: JSONArray()
        val entries = mutableListOf<DailyEntry>()
        for (i in 0 until arr.length()) {
            entries.add(entryFromJson(arr.getJSONObject(i)))
        }
        val tombsObj = root.optJSONObject("tombstones") ?: JSONObject()
        val tombs = mutableMapOf<Long, Long>()
        val keys = tombsObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            tombs[k.toLong()] = tombsObj.getLong(k)
        }
        return Snapshot(entries, tombs)
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
        o.put("hoursSource", e.hoursSource)
        o.put("noLunchTaken", e.noLunchTaken)
        return o
    }

    private fun entryFromJson(o: JSONObject): DailyEntry {
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
            breakPaid = o.optBoolean("breakPaid", false),
            hoursSource = o.optString("hoursSource", HoursSource.CLOCK.name)
                .ifBlank { HoursSource.CLOCK.name },
            noLunchTaken = o.optBoolean("noLunchTaken", false)
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
