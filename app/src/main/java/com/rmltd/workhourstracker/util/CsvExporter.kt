package com.rmltd.workhourstracker.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.rmltd.workhourstracker.data.DailyEntry
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds a CSV of daily rows and shares it via the system share sheet (FileProvider).
 * [buildCsv] is pure JVM (no Context) so unit tests can cover headers/rows/escaping.
 */
object CsvExporter {

    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    /** Export clocks are Locale.US so CSV content is stable across devices. */
    private val clockFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun buildCsv(weeks: List<Pair<LocalDate, List<DailyEntry>>>): String {
        val sb = StringBuilder()
        sb.appendLine(
            "week_start,date,clock_in,clock_out,lunch_out,lunch_in," +
                "breakDurationMinutes,breakPaid,hours,comments"
        )
        for ((weekStart, entries) in weeks) {
            val weekStartStr = weekStart.format(dateFmt)
            for (entry in entries.sortedBy { it.dateEpochDay }) {
                sb.append(weekStartStr).append(',')
                sb.append(LocalDate.ofEpochDay(entry.dateEpochDay).format(dateFmt)).append(',')
                sb.append(csvClock(entry.clockInMinutes)).append(',')
                sb.append(csvClock(entry.clockOutMinutes)).append(',')
                sb.append(csvClock(entry.lunchOutMinutes)).append(',')
                sb.append(csvClock(entry.lunchInMinutes)).append(',')
                sb.append(entry.breakDurationMinutes?.toString().orEmpty()).append(',')
                sb.append(if (entry.breakPaid) "true" else "false").append(',')
                sb.append("%.2f".format(java.util.Locale.US, entry.hoursWorked)).append(',')
                sb.append(csvEscape(entry.comments))
                sb.appendLine()
            }
        }
        return sb.toString()
    }

    /**
     * Keeps only entries whose date is within [[startInclusive], [endInclusive]].
     * Weeks that become empty after filtering are dropped. Null bound = unbounded.
     */
    fun filterByDateRange(
        weeks: List<Pair<LocalDate, List<DailyEntry>>>,
        startInclusive: LocalDate? = null,
        endInclusive: LocalDate? = null
    ): List<Pair<LocalDate, List<DailyEntry>>> {
        if (startInclusive == null && endInclusive == null) return weeks
        val startDay = startInclusive?.toEpochDay()
        val endDay = endInclusive?.toEpochDay()
        val out = ArrayList<Pair<LocalDate, List<DailyEntry>>>()
        for ((weekStart, entries) in weeks) {
            val filtered = entries.filter { e ->
                val d = e.dateEpochDay
                (startDay == null || d >= startDay) && (endDay == null || d <= endDay)
            }
            if (filtered.isNotEmpty()) {
                out.add(weekStart to filtered.sortedBy { it.dateEpochDay })
            }
        }
        return out
    }

    fun buildCsvForDateRange(
        weeks: List<Pair<LocalDate, List<DailyEntry>>>,
        startInclusive: LocalDate? = null,
        endInclusive: LocalDate? = null
    ): String = buildCsv(filterByDateRange(weeks, startInclusive, endInclusive))

    fun shareCsv(context: Context, csv: String, fileName: String = "work_hours_export.csv"): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(csv, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Work Hours Tracker export")
            // ClipData is required on some OEMs for FileProvider URI grants to stick
            clipData = android.content.ClipData.newRawUri("work_hours", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun csvClock(minutes: Int?): String {
        if (minutes == null) return ""
        val time = LocalTime.of(minutes / 60, minutes % 60)
        return time.format(clockFmt)
    }

    private fun csvEscape(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
