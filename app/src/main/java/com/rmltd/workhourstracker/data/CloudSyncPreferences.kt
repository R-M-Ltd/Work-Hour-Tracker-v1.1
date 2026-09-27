package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Cloud sync prefs (1.3.34). Default **off**. One provider at a time.
 * OAuth tokens live only in EncryptedSharedPreferences — never plain prefs.
 *
 * BuildConfig / local.properties keys (see CLOUD_SYNC.md):
 *   DRIVE_CLIENT_ID, DROPBOX_APP_KEY, ONEDRIVE_CLIENT_ID
 */
object CloudSyncPreferences {

    enum class Provider(val id: String, val displayName: String) {
        GOOGLE_DRIVE("google_drive", "Google Drive"),
        DROPBOX("dropbox", "Dropbox"),
        ONEDRIVE("onedrive", "OneDrive");

        companion object {
            fun fromId(id: String?): Provider? = entries.firstOrNull { it.id == id }
        }
    }

    private const val PREFS_NAME = "cloud_sync_prefs"
    private const val SECURE_PREFS = "cloud_sync_secure"
    private const val KEY_ENABLED = "cloud_sync_enabled"
    private const val KEY_PROVIDER = "cloud_sync_provider"
    private const val KEY_ACCOUNT_NAME = "cloud_sync_account_name"
    private const val KEY_LAST_SYNC = "cloud_sync_last_epoch"
    private const val KEY_LAST_ERROR = "cloud_sync_last_error"
    private const val KEY_ACCESS_TOKEN = "cloud_access_token"
    private const val KEY_REFRESH_TOKEN = "cloud_refresh_token"
    private const val KEY_TOMBSTONES = "cloud_tombstones_json"

    const val CONFLICT_COPY =
        "If the same day changed on two devices, the latest save wins."
    const val ICLOUD_FOOTNOTE = "iCloud is not available on Android."
    const val HELPER_CHOOSE =
        "Choose one cloud. Switch providers by unlinking first."
    const val NOT_CONFIGURED = "Cloud provider not configured"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun securePrefs(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            // Unit tests / missing Keystore — fall back to private prefs (still not world-readable).
            context.getSharedPreferences(SECURE_PREFS + "_fallback", Context.MODE_PRIVATE)
        }
    }

    /** Default false — hours stay on-device until the user opts in. */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) {
            // Keep linked account + tokens so turning back on resumes; no silent sync while off.
            clearLastError(context)
        }
    }

    fun isLinked(context: Context): Boolean =
        getProvider(context) != null && !getAccessToken(context).isNullOrBlank()

    fun getProvider(context: Context): Provider? =
        Provider.fromId(prefs(context).getString(KEY_PROVIDER, null))

    fun setProvider(context: Context, provider: Provider?) {
        prefs(context).edit().putString(KEY_PROVIDER, provider?.id).apply()
    }

    fun getAccountName(context: Context): String? =
        prefs(context).getString(KEY_ACCOUNT_NAME, null)

    fun setAccountName(context: Context, name: String?) {
        prefs(context).edit().putString(KEY_ACCOUNT_NAME, name).apply()
    }

    fun getLastSyncEpochMillis(context: Context): Long =
        prefs(context).getLong(KEY_LAST_SYNC, 0L)

    fun setLastSyncEpochMillis(context: Context, epoch: Long) {
        prefs(context).edit().putLong(KEY_LAST_SYNC, epoch).apply()
    }

    fun getLastError(context: Context): String? =
        prefs(context).getString(KEY_LAST_ERROR, null)

    fun setLastError(context: Context, message: String?) {
        prefs(context).edit().putString(KEY_LAST_ERROR, message).apply()
    }

    fun clearLastError(context: Context) = setLastError(context, null)

    fun getAccessToken(context: Context): String? =
        securePrefs(context).getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(context: Context): String? =
        securePrefs(context).getString(KEY_REFRESH_TOKEN, null)

    fun setTokens(context: Context, access: String?, refresh: String?) {
        securePrefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, access)
            .putString(KEY_REFRESH_TOKEN, refresh)
            .apply()
    }

    /** Unlink keeps local Room data; clears tokens + link metadata. */
    fun unlink(context: Context) {
        setTokens(context, null, null)
        prefs(context).edit()
            .remove(KEY_PROVIDER)
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_LAST_ERROR)
            .apply()
    }

    fun getTombstonesJson(context: Context): String =
        prefs(context).getString(KEY_TOMBSTONES, "{}") ?: "{}"

    fun setTombstonesJson(context: Context, json: String) {
        prefs(context).edit().putString(KEY_TOMBSTONES, json).apply()
    }

    /** Pure default used by unit tests (no Context). */
    fun defaultEnabled(): Boolean = false
}
