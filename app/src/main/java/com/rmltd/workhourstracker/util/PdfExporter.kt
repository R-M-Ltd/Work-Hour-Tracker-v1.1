package com.rmltd.workhourstracker.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.rmltd.workhourstracker.data.DailyEntry
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Builds a simple printable timesheet PDF and shares it via FileProvider + ACTION_SEND.
 * [buildRows] / [totalHours] / [rangeLabel] are pure JVM for unit tests;
 * [writePdf] / [sharePdf] need Android [PdfDocument].
 */
object PdfExporter {

    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private const val PAGE_WIDTH = 612 // US Letter points (8.5in @ 72dpi)
    private const val PAGE_HEIGHT = 792 // 11in
    private const val MARGIN = 48f
    private const val LINE_HEIGHT = 18f

    data class TimesheetRow(
        val dateIso: String,
        val dayName: String,
        val clockRange: String,
        val hours: Double
    )

    fun buildRows(weeks: List<Pair<LocalDate, List<DailyEntry>>>): List<TimesheetRow> {
        val rows = ArrayList<TimesheetRow>()
        for ((_, entries) in weeks) {
            for (entry in entries.sortedBy { it.dateEpochDay }) {
                val date = LocalDate.ofEpochDay(entry.dateEpochDay)
                val range = HoursCalc.formatRange(entry.clockInMinutes, entry.clockOutMinutes)
                    ?: "—"
                rows.add(
                    TimesheetRow(
                        dateIso = date.format(dateFmt),
                        dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US),
                        clockRange = range,
                        hours = entry.hoursWorked
                    )
                )
            }
        }
        return rows.sortedBy { it.dateIso }
    }

    fun totalHours(rows: List<TimesheetRow>): Double =
        rows.sumOf { it.hours }

    fun rangeLabel(
        rows: List<TimesheetRow>,
        startInclusive: LocalDate? = null,
        endInclusive: LocalDate? = null
    ): String {
        if (startInclusive != null && endInclusive != null) {
            return "${startInclusive.format(dateFmt)} → ${endInclusive.format(dateFmt)}"
        }
        if (rows.isEmpty()) return "No dates"
        return "${rows.first().dateIso} → ${rows.last().dateIso}"
    }

    fun writePdf(
        context: Context,
        rows: List<TimesheetRow>,
        rangeText: String,
        fileName: String = "work_hours_timesheet.pdf"
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        val doc = PdfDocument()
        try {
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = doc.startPage(pageInfo)
            var canvas = page.canvas
            var y = MARGIN

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = 0xFF1D1A22.toInt()
            }
            val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = 0xFF1D1A22.toInt()
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 11f
                color = 0xFF1D1A22.toInt()
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10f
                color = 0xFF49454E.toInt()
            }
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = 0xFF1D1A22.toInt()
            }

            fun finishAndStartNew() {
                doc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN
                drawColumnHeader(canvas, headerPaint, y)
                y += LINE_HEIGHT + 4f
            }

            fun ensureSpace(needed: Float) {
                if (y + needed > PAGE_HEIGHT - MARGIN) {
                    finishAndStartNew()
                }
            }

            canvas.drawText("Work Hours Timesheet", MARGIN, y, titlePaint)
            y += LINE_HEIGHT + 4f
            canvas.drawText(rangeText, MARGIN, y, mutedPaint)
            y += LINE_HEIGHT + 8f
            drawColumnHeader(canvas, headerPaint, y)
            y += LINE_HEIGHT + 4f

            if (rows.isEmpty()) {
                canvas.drawText("No hours in this range.", MARGIN, y, bodyPaint)
                y += LINE_HEIGHT
            } else {
                for (row in rows) {
                    ensureSpace(LINE_HEIGHT)
                    val hoursStr = "%.2f".format(Locale.US, row.hours)
                    canvas.drawText(row.dateIso, MARGIN, y, bodyPaint)
                    canvas.drawText(row.dayName, MARGIN + 90f, y, bodyPaint)
                    canvas.drawText(row.clockRange, MARGIN + 140f, y, bodyPaint)
                    canvas.drawText(hoursStr, MARGIN + 320f, y, bodyPaint)
                    y += LINE_HEIGHT
                }
            }

            y += 8f
            ensureSpace(LINE_HEIGHT * 2)
            val total = totalHours(rows)
            canvas.drawText(
                "Total hours: %.2f".format(Locale.US, total),
                MARGIN,
                y,
                totalPaint
            )

            doc.finishPage(page)
            FileOutputStream(file).use { out -> doc.writeTo(out) }
        } finally {
            doc.close()
        }
        return file
    }

    private fun drawColumnHeader(canvas: Canvas, paint: Paint, y: Float) {
        canvas.drawText("Date", MARGIN, y, paint)
        canvas.drawText("Day", MARGIN + 90f, y, paint)
        canvas.drawText("Clock", MARGIN + 140f, y, paint)
        canvas.drawText("Hours", MARGIN + 320f, y, paint)
    }

    fun sharePdf(
        context: Context,
        weeks: List<Pair<LocalDate, List<DailyEntry>>>,
        startInclusive: LocalDate? = null,
        endInclusive: LocalDate? = null,
        fileName: String = "work_hours_timesheet.pdf"
    ): Intent {
        val filtered = CsvExporter.filterByDateRange(weeks, startInclusive, endInclusive)
        val rows = buildRows(filtered)
        val rangeText = rangeLabel(rows, startInclusive, endInclusive)
        val file = writePdf(context, rows, rangeText, fileName)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Work Hours Tracker timesheet")
            clipData = ClipData.newRawUri("work_hours_pdf", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
