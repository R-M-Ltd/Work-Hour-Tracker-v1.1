package com.rmltd.workhourstracker.data.sync

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal object DriveRest {
    private const val FILES = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    fun downloadAppDataFile(accessToken: String, name: String): String? {
        val q = URLEncoder.encode(
            "name='$name' and 'appDataFolder' in parents and trashed=false",
            "UTF-8"
        )
        val listUrl = "$FILES?spaces=appDataFolder&q=$q&fields=files(id,name)"
        val list = httpGet(listUrl, accessToken) ?: return null
        val files = JSONObject(list).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        val id = files.getJSONObject(0).getString("id")
        return httpGet("$FILES/$id?alt=media", accessToken)
    }

    fun uploadAppDataFile(accessToken: String, name: String, body: String) {
        val q = URLEncoder.encode(
            "name='$name' and 'appDataFolder' in parents and trashed=false",
            "UTF-8"
        )
        val list = httpGet(
            "$FILES?spaces=appDataFolder&q=$q&fields=files(id,name)",
            accessToken
        )
        val existingId = list?.let {
            val files = JSONObject(it).optJSONArray("files")
            if (files != null && files.length() > 0) files.getJSONObject(0).getString("id") else null
        }
        if (existingId != null) {
            httpPatchBinary(
                "$UPLOAD/$existingId?uploadType=media",
                accessToken,
                body,
                "application/json"
            )
        } else {
            val metadata = JSONObject()
                .put("name", name)
                .put("parents", org.json.JSONArray().put("appDataFolder"))
                .toString()
            httpMultipartUpload(UPLOAD + "?uploadType=multipart", accessToken, metadata, body)
        }
    }
}

internal object DropboxRest {
    fun download(accessToken: String, path: String): String? {
        val conn = (URL("https://content.dropboxapi.com/2/files/download").openConnection()
            as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Dropbox-API-Arg", JSONObject().put("path", path).toString())
        return try {
            if (conn.responseCode in 200..299) read(conn) else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun upload(accessToken: String, path: String, body: String) {
        val conn = (URL("https://content.dropboxapi.com/2/files/upload").openConnection()
            as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setRequestProperty(
            "Dropbox-API-Arg",
            JSONObject()
                .put("path", path)
                .put("mode", "overwrite")
                .put("autorename", false)
                .toString()
        )
        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) error("Dropbox upload failed: $code")
    }
}

internal object GraphRest {
    private const val ROOT =
        "https://graph.microsoft.com/v1.0/me/drive/special/approot:/"

    fun downloadAppFolderFile(accessToken: String, name: String): String? =
        httpGet("$ROOT$name:/content", accessToken)

    fun uploadAppFolderFile(accessToken: String, name: String, body: String) {
        httpPutBinary("$ROOT$name:/content", accessToken, body, "application/json")
    }
}

private fun httpGet(url: String, accessToken: String): String? {
    val conn = (URL(url).openConnection() as HttpURLConnection)
    conn.requestMethod = "GET"
    conn.setRequestProperty("Authorization", "Bearer $accessToken")
    return try {
        if (conn.responseCode in 200..299) read(conn) else null
    } catch (_: Exception) {
        null
    } finally {
        conn.disconnect()
    }
}

private fun httpPutBinary(url: String, accessToken: String, body: String, mime: String) {
    val conn = (URL(url).openConnection() as HttpURLConnection)
    conn.requestMethod = "PUT"
    conn.doOutput = true
    conn.setRequestProperty("Authorization", "Bearer $accessToken")
    conn.setRequestProperty("Content-Type", mime)
    OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
    val code = conn.responseCode
    conn.disconnect()
    if (code !in 200..299) error("Upload failed: $code")
}

private fun httpPatchBinary(url: String, accessToken: String, body: String, mime: String) {
    val conn = (URL(url).openConnection() as HttpURLConnection)
    conn.requestMethod = "PATCH"
    conn.doOutput = true
    conn.setRequestProperty("Authorization", "Bearer $accessToken")
    conn.setRequestProperty("Content-Type", mime)
    OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(body) }
    val code = conn.responseCode
    conn.disconnect()
    if (code !in 200..299) error("Patch failed: $code")
}

private fun httpMultipartUpload(
    url: String,
    accessToken: String,
    metadataJson: String,
    body: String
) {
    val boundary = "----wht" + System.currentTimeMillis()
    val conn = (URL(url).openConnection() as HttpURLConnection)
    conn.requestMethod = "POST"
    conn.doOutput = true
    conn.setRequestProperty("Authorization", "Bearer $accessToken")
    conn.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
    conn.outputStream.use { os ->
        val w = OutputStreamWriter(os, StandardCharsets.UTF_8)
        w.write("--$boundary\r\n")
        w.write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        w.write(metadataJson)
        w.write("\r\n--$boundary\r\n")
        w.write("Content-Type: application/json\r\n\r\n")
        w.write(body)
        w.write("\r\n--$boundary--\r\n")
        w.flush()
    }
    val code = conn.responseCode
    conn.disconnect()
    if (code !in 200..299) error("Multipart upload failed: $code")
}

private fun read(conn: HttpURLConnection): String =
    BufferedReader(InputStreamReader(conn.inputStream, StandardCharsets.UTF_8)).use { it.readText() }
