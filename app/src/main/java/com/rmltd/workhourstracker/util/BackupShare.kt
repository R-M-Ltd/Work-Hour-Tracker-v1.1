package com.rmltd.workhourstracker.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Writes a backup JSON into cache/exports and builds a share [Intent]
 * using the same FileProvider + ClipData pattern as [CsvExporter].
 */
object BackupShare {

    fun shareBackup(
        context: Context,
        json: String,
        fileName: String = BackupCodec.FILE_NAME
    ): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(json, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = BackupCodec.MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Work Hours Tracker backup")
            clipData = ClipData.newRawUri("work_hours_backup", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
