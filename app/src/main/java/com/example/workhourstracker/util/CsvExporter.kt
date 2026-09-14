package com.example.workhourstracker.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.workhourstracker.data.DailyEntry
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Builds a CSV of daily rows and shares it via the system share sheet (FileProvider).
 */
object CsvExporter {

    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun buildCsv(weeks: List<Pair<LocalDate, List<DailyEntry>>>): String {
        val sb = StringBuilder()
        sb.appendLine("week_start,date,clock_in,clock_out,lunch_out,lunch_in,hours,comments")
        for ((weekStart, entries) in weeks) {
            val weekStartStr = weekStart.format(dateFmt)
            for (entry in entries.sortedBy { it.dateEpochDay }) {
                sb.append(weekStartStr).append(',')
                sb.append(LocalDate.ofEpochDay(entry.dateEpochDay).format(dateFmt)).append(',')
                sb.append(csvClock(entry.clockInMinutes)).append(',')
                sb.append(csvClock(entry.clockOutMinutes)).append(',')
                sb.append(csvClock(entry.lunchOutMinutes)).append(',')
                sb.append(csvClock(entry.lunchInMinutes)).append(',')
                sb.append("%.2f".format(java.util.Locale.US, entry.hoursWorked)).append(',')
                sb.append(csvEscape(entry.comments))
                sb.appendLine()
            }
        }
        return sb.toString()
    }

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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun csvClock(minutes: Int?): String =
        if (minutes == null) "" else HoursCalc.formatClock(minutes)

    private fun csvEscape(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
