package com.rmltd.workhourstracker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Cloud sync prefs (1.3.35). Default **off**. One provider at a time.
 * OAuth tokens live only in EncryptedSharedPreferences — never plain prefs.
 * Auth-code + refresh: [KEY_REFRESH_TOKEN], [KEY_SESSION_EXPIRED].
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
    private const val KEY_ACCESS_EXPIRES_AT = "cloud_access_expires_at"
    private const val KEY_SESSION_EXPIRED = "cloud_session_expired"
    private const val KEY_TOMBSTONES = "cloud_tombstones_json"
    private const val KEY_PENDING_PKCE = "cloud_pending_pkce"
    private const val KEY_PENDING_PKCE_PROVIDER = "cloud_pending_pkce_provider"

    const val CONFLICT_COPY =
        "If the same day changed on two devices, the latest save wins."
    const val ICLOUD_FOOTNOTE = "iCloud is not available on Android."
    const val HELPER_CHOOSE =
        "Choose one cloud. Switch providers by unlinking first."
    const val NOT_CONFIGURED = "Cloud provider not configured"
    const val SESSION_EXPIRED =
        "Session expired — sign in again to keep syncing."
    const val SIGN_IN_AGAIN = "Sign in again"
    const val AUTH_CODE_EDUCATION =
        "You'll approve access in your browser. We use a secure sign-in so sync can refresh without asking every time."
    const val SIGNING_IN = "Opening your cloud account…"

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

    /**
     * Linked when a provider is chosen and we still have credentials or a
     * session-expired link that needs Sign in again (account may remain).
     */
    fun isLinked(context: Context): Boolean {
        if (getProvider(context) == null) return false
        if (isSessionExpired(context)) return true
        return !getAccessToken(context).isNullOrBlank() ||
            !getRefreshToken(context).isNullOrBlank()
    }

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

    fun isSessionExpired(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SESSION_EXPIRED, false)

    fun setSessionExpired(context: Context, expired: Boolean) {
        prefs(context).edit().putBoolean(KEY_SESSION_EXPIRED, expired).apply()
    }

    /** Marks link dead for sync; keeps provider + account for Sign in again UX. */
    fun markSessionExpired(context: Context) {
        setTokens(context, null, null)
        setAccessExpiresAt(context, 0L)
        setSessionExpired(context, true)
        setLastError(context, SESSION_EXPIRED)
    }

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

    /** Persist access (+ optional new refresh) without wiping refresh when [refresh] is null. */
    fun updateAccessToken(context: Context, access: String, refresh: String? = null, expiresAtEpochMs: Long = 0L) {
        val ed = securePrefs(context).edit().putString(KEY_ACCESS_TOKEN, access)
        if (refresh != null) ed.putString(KEY_REFRESH_TOKEN, refresh)
        ed.apply()
        setAccessExpiresAt(context, expiresAtEpochMs)
        setSessionExpired(context, false)
        clearLastError(context)
    }

    fun getAccessExpiresAt(context: Context): Long =
        prefs(context).getLong(KEY_ACCESS_EXPIRES_AT, 0L)

    fun setAccessExpiresAt(context: Context, epochMs: Long) {
        prefs(context).edit().putLong(KEY_ACCESS_EXPIRES_AT, epochMs).apply()
    }

    fun setPendingPkce(context: Context, verifier: String, provider: Provider) {
        prefs(context).edit()
            .putString(KEY_PENDING_PKCE, verifier)
            .putString(KEY_PENDING_PKCE_PROVIDER, provider.id)
            .apply()
    }

    fun getPendingPkceVerifier(context: Context): String? =
        prefs(context).getString(KEY_PENDING_PKCE, null)

    fun getPendingPkceProvider(context: Context): Provider? =
        Provider.fromId(prefs(context).getString(KEY_PENDING_PKCE_PROVIDER, null))

    fun clearPendingPkce(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_PKCE)
            .remove(KEY_PENDING_PKCE_PROVIDER)
            .apply()
    }

    /** Unlink keeps local Room data; clears tokens + link metadata. */
    fun unlink(context: Context) {
        setTokens(context, null, null)
        setAccessExpiresAt(context, 0L)
        setSessionExpired(context, false)
        clearPendingPkce(context)
        prefs(context).edit()
            .remove(KEY_PROVIDER)
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_LAST_ERROR)
            .remove(KEY_SESSION_EXPIRED)
            .remove(KEY_ACCESS_EXPIRES_AT)
            .apply()
    }

    fun getTombstonesJson(context: Context): String =
        prefs(context).getString(KEY_TOMBSTONES, "{}") ?: "{}"

    fun setTombstonesJson(context: Context, json: String) {
        prefs(context).edit().putString(KEY_TOMBSTONES, json).apply()
    }

    /** Pure default used by unit tests (no Context). */
    fun defaultEnabled(): Boolean = false

    /** Pure helper for UI / tests: treat lastError or flag as session-expired state. */
    fun isSessionExpiredSignal(lastError: String?, sessionExpiredFlag: Boolean): Boolean =
        sessionExpiredFlag || lastError == SESSION_EXPIRED
}
