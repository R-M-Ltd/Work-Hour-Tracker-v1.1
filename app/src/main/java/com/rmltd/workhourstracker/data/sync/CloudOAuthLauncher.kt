package com.rmltd.workhourstracker.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.rmltd.workhourstracker.BuildConfig
import com.rmltd.workhourstracker.data.CloudSyncPreferences
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Builds provider OAuth authorize URLs (authorization-code + PKCE) and completes
 * the redirect by exchanging `code` for access + refresh tokens.
 *
 * Redirect URI must be registered with each provider console and match the app's
 * intent-filter (com.rmltd.workhourstracker://oauth).
 */
object CloudOAuthLauncher {

    const val REDIRECT_URI = "com.rmltd.workhourstracker://oauth"

    fun authorizeIntent(context: Context, provider: CloudSyncPreferences.Provider): Intent? {
        val verifier = Pkce.generateCodeVerifier()
        val challenge = Pkce.codeChallengeS256(verifier)
        CloudSyncPreferences.setPendingPkce(context, verifier, provider)
        val url = authorizeUrl(provider, challenge) ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    fun authorizeUrl(
        provider: CloudSyncPreferences.Provider,
        challenge: String? = null
    ): String? {
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
        val redirect = enc(REDIRECT_URI)
        val pkce = if (!challenge.isNullOrBlank()) {
            "&code_challenge=${enc(challenge)}" +
                "&code_challenge_method=S256"
        } else ""
        return when (provider) {
            CloudSyncPreferences.Provider.GOOGLE_DRIVE -> {
                val id = BuildConfig.DRIVE_CLIENT_ID
                if (id.isBlank()) return null
                "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?client_id=${enc(id)}" +
                    "&redirect_uri=$redirect" +
                    "&response_type=code" +
                    "&scope=${enc("https://www.googleapis.com/auth/drive.appdata")}" +
                    "&access_type=offline" +
                    "&prompt=consent" +
                    pkce
            }
            CloudSyncPreferences.Provider.DROPBOX -> {
                val key = BuildConfig.DROPBOX_APP_KEY
                if (key.isBlank()) return null
                "https://www.dropbox.com/oauth2/authorize" +
                    "?client_id=${enc(key)}" +
                    "&redirect_uri=$redirect" +
                    "&response_type=code" +
                    "&token_access_type=offline" +
                    pkce
            }
            CloudSyncPreferences.Provider.ONEDRIVE -> {
                val id = BuildConfig.ONEDRIVE_CLIENT_ID
                if (id.isBlank()) return null
                "https://login.microsoftonline.com/common/oauth2/v2.0/authorize" +
                    "?client_id=${enc(id)}" +
                    "&redirect_uri=$redirect" +
                    "&response_type=code" +
                    "&scope=${enc("Files.ReadWrite offline_access User.Read")}" +
                    pkce
            }
        }
    }

    /**
     * Parse redirect for authorization `code` (or legacy fragment token), exchange,
     * and persist link state. Network — call off the main thread.
     * @return true if tokens were applied
     */
    fun applyAuthRedirect(
        context: Context,
        uri: Uri,
        provider: CloudSyncPreferences.Provider
    ): Boolean {
        val fragment = uri.fragment.orEmpty()
        val query = uri.query.orEmpty()
        val combined = if (query.isNotEmpty()) query else fragment
        val params = combined.split("&").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to Uri.decode(it.substring(i + 1))
        }.toMap()

        // Legacy implicit fragment (upgrade path / old redirects)
        val legacyAccess = params["access_token"]
        if (!legacyAccess.isNullOrBlank() && params["code"].isNullOrBlank()) {
            val refresh = params["refresh_token"]
            CloudSyncPreferences.setProvider(context, provider)
            CloudSyncPreferences.setTokens(context, legacyAccess, refresh)
            CloudSyncPreferences.setAccessExpiresAt(context, 0L)
            CloudSyncPreferences.setSessionExpired(context, false)
            val name = params["account_id"]
                ?: params["email"]
                ?: provider.displayName
            CloudSyncPreferences.setAccountName(context, name)
            CloudSyncPreferences.clearLastError(context)
            CloudSyncPreferences.clearPendingPkce(context)
            return true
        }

        val code = params["code"] ?: return false
        val verifier = CloudSyncPreferences.getPendingPkceVerifier(context)
        return try {
            val tokens = OAuthTokenExchange.exchangeCode(provider, code, verifier)
            CloudSyncPreferences.setProvider(context, provider)
            CloudSyncPreferences.setTokens(context, tokens.accessToken, tokens.refreshToken)
            val expiresAt = if (tokens.expiresInSec > 0) {
                System.currentTimeMillis() + tokens.expiresInSec * 1000L
            } else 0L
            CloudSyncPreferences.setAccessExpiresAt(context, expiresAt)
            CloudSyncPreferences.setSessionExpired(context, false)
            val name = params["account_id"]
                ?: tokens.accountHint
                ?: provider.displayName
            CloudSyncPreferences.setAccountName(context, name)
            CloudSyncPreferences.clearLastError(context)
            CloudSyncPreferences.clearPendingPkce(context)
            true
        } catch (_: Exception) {
            CloudSyncPreferences.clearPendingPkce(context)
            false
        }
    }

    fun isConfigured(provider: CloudSyncPreferences.Provider): Boolean =
        CloudProviderFactory.clientFor(provider).isConfigured()
}

/** PKCE helpers (RFC 7636) — pure for unit tests. */
object Pkce {
    private val unreserved =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    fun generateCodeVerifier(length: Int = 64): String {
        val rnd = SecureRandom()
        return buildString(length) {
            repeat(length) { append(unreserved[rnd.nextInt(unreserved.length)]) }
        }
    }

    fun codeChallengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}

internal data class OAuthTokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSec: Long,
    val accountHint: String? = null
)

internal object OAuthTokenExchange {

    fun exchangeCode(
        provider: CloudSyncPreferences.Provider,
        code: String,
        codeVerifier: String?
    ): OAuthTokenResponse {
        val body = LinkedHashMap<String, String>()
        body["grant_type"] = "authorization_code"
        body["code"] = code
        body["redirect_uri"] = CloudOAuthLauncher.REDIRECT_URI
        if (!codeVerifier.isNullOrBlank()) body["code_verifier"] = codeVerifier
        when (provider) {
            CloudSyncPreferences.Provider.GOOGLE_DRIVE -> {
                body["client_id"] = BuildConfig.DRIVE_CLIENT_ID
                return postForm("https://oauth2.googleapis.com/token", body)
            }
            CloudSyncPreferences.Provider.DROPBOX -> {
                body["client_id"] = BuildConfig.DROPBOX_APP_KEY
                return postForm("https://api.dropboxapi.com/oauth2/token", body)
            }
            CloudSyncPreferences.Provider.ONEDRIVE -> {
                body["client_id"] = BuildConfig.ONEDRIVE_CLIENT_ID
                body["scope"] = "Files.ReadWrite offline_access User.Read"
                return postForm(
                    "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                    body
                )
            }
        }
    }

    fun refresh(
        provider: CloudSyncPreferences.Provider,
        refreshToken: String
    ): OAuthTokenResponse {
        val body = LinkedHashMap<String, String>()
        body["grant_type"] = "refresh_token"
        body["refresh_token"] = refreshToken
        when (provider) {
            CloudSyncPreferences.Provider.GOOGLE_DRIVE -> {
                body["client_id"] = BuildConfig.DRIVE_CLIENT_ID
                return postForm("https://oauth2.googleapis.com/token", body)
            }
            CloudSyncPreferences.Provider.DROPBOX -> {
                body["client_id"] = BuildConfig.DROPBOX_APP_KEY
                return postForm("https://api.dropboxapi.com/oauth2/token", body)
            }
            CloudSyncPreferences.Provider.ONEDRIVE -> {
                body["client_id"] = BuildConfig.ONEDRIVE_CLIENT_ID
                body["scope"] = "Files.ReadWrite offline_access User.Read"
                return postForm(
                    "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                    body
                )
            }
        }
    }

    private fun postForm(url: String, fields: Map<String, String>): OAuthTokenResponse {
        val encoded = fields.entries.joinToString("&") { (k, v) ->
            java.net.URLEncoder.encode(k, "UTF-8") + "=" +
                java.net.URLEncoder.encode(v, "UTF-8")
        }
        val conn = (URL(url).openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { it.write(encoded) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) error("Token exchange failed: $code $raw")
        val json = JSONObject(raw)
        val access = json.getString("access_token")
        val refresh = if (json.has("refresh_token") && !json.isNull("refresh_token")) {
            json.getString("refresh_token").takeIf { it.isNotBlank() }
        } else null
        val expires = json.optLong("expires_in", 0L)
        return OAuthTokenResponse(access, refresh, expires)
    }
}
