package com.rmltd.workhourstracker.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.rmltd.workhourstracker.BuildConfig
import com.rmltd.workhourstracker.data.CloudSyncPreferences

/**
 * Builds provider OAuth authorize URLs. Settings opens these in a browser /
 * Custom Tab. Redirect URI must be registered with each provider console and
 * match the app's intent-filter (com.rmltd.workhourstracker://oauth).
 *
 * Tokens are applied via [applyAuthRedirect] when the redirect deep-link returns.
 */
object CloudOAuthLauncher {

    const val REDIRECT_URI = "com.rmltd.workhourstracker://oauth"

    fun authorizeIntent(provider: CloudSyncPreferences.Provider): Intent? {
        val url = authorizeUrl(provider) ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    fun authorizeUrl(provider: CloudSyncPreferences.Provider): String? {
        val redirect = Uri.encode(REDIRECT_URI)
        return when (provider) {
            CloudSyncPreferences.Provider.GOOGLE_DRIVE -> {
                val id = BuildConfig.DRIVE_CLIENT_ID
                if (id.isBlank()) return null
                "https://accounts.google.com/o/oauth2/v2/auth" +
                    "?client_id=${Uri.encode(id)}" +
                    "&redirect_uri=$redirect" +
                    "&response_type=token" +
                    "&scope=${Uri.encode("https://www.googleapis.com/auth/drive.appdata")}"
            }
            CloudSyncPreferences.Provider.DROPBOX -> {
                val key = BuildConfig.DROPBOX_APP_KEY
                if (key.isBlank()) return null
                "https://www.dropbox.com/oauth2/authorize" +
                    "?client_id=${Uri.encode(key)}" +
                    "&redirect_uri=$redirect" +
                    "&response_type=token" +
                    "&token_access_type=offline"
            }
            CloudSyncPreferences.Provider.ONEDRIVE -> {
                val id = BuildConfig.ONEDRIVE_CLIENT_ID
                if (id.isBlank()) return null
                "https://login.microsoftonline.com/common/oauth2/v2.0/authorize" +
                    "?client_id=${Uri.encode(id)}" +
                    "&redirect_uri=$redirect" +
                    "&response_type=token" +
                    "&scope=${Uri.encode("Files.ReadWrite offline_access User.Read")}"
            }
        }
    }

    /**
     * Parse redirect URI fragment/query for access_token and persist link state.
     * @return true if a token was applied
     */
    fun applyAuthRedirect(
        context: Context,
        uri: Uri,
        provider: CloudSyncPreferences.Provider
    ): Boolean {
        val fragment = uri.fragment.orEmpty()
        val query = uri.query.orEmpty()
        val combined = if (fragment.isNotEmpty()) fragment else query
        val params = combined.split("&").mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to Uri.decode(it.substring(i + 1))
        }.toMap()
        val access = params["access_token"] ?: return false
        val refresh = params["refresh_token"]
        CloudSyncPreferences.setProvider(context, provider)
        CloudSyncPreferences.setTokens(context, access, refresh)
        val name = params["account_id"]
            ?: params["email"]
            ?: provider.displayName
        CloudSyncPreferences.setAccountName(context, name)
        CloudSyncPreferences.clearLastError(context)
        return true
    }

    fun isConfigured(provider: CloudSyncPreferences.Provider): Boolean =
        CloudProviderFactory.clientFor(provider).isConfigured()
}
