package com.rmltd.workhourstracker.data.sync

import android.content.Context
import com.rmltd.workhourstracker.BuildConfig
import com.rmltd.workhourstracker.data.CloudSyncPreferences

/**
 * Provider client interface. Sign-in uses OAuth when client IDs are present in
 * BuildConfig (from local.properties). Missing keys → clear "not configured" error.
 */
interface CloudProviderClient {
    val provider: CloudSyncPreferences.Provider
    fun isConfigured(): Boolean
    /**
     * Begin OAuth. Returns access token + display name on success.
     * Implementations may launch Activities; for unit tests use Fake.
     */
    suspend fun signIn(context: Context): SignInResult
    suspend fun downloadSyncFile(context: Context, accessToken: String): String?
    suspend fun uploadSyncFile(context: Context, accessToken: String, json: String)
}

sealed class SignInResult {
    data class Success(val accessToken: String, val refreshToken: String?, val accountName: String) :
        SignInResult()
    data class Failure(val message: String) : SignInResult()
}

object CloudProviderFactory {
    fun clientFor(provider: CloudSyncPreferences.Provider): CloudProviderClient = when (provider) {
        CloudSyncPreferences.Provider.GOOGLE_DRIVE -> GoogleDriveClient()
        CloudSyncPreferences.Provider.DROPBOX -> DropboxClient()
        CloudSyncPreferences.Provider.ONEDRIVE -> OneDriveClient()
    }
}

/** Google Drive via OAuth token + Drive REST appDataFolder. */
class GoogleDriveClient : CloudProviderClient {
    override val provider = CloudSyncPreferences.Provider.GOOGLE_DRIVE
    override fun isConfigured(): Boolean = BuildConfig.DRIVE_CLIENT_ID.isNotBlank()

    override suspend fun signIn(context: Context): SignInResult {
        if (!isConfigured()) {
            return SignInResult.Failure(CloudSyncPreferences.NOT_CONFIGURED)
        }
        // Real Google Sign-In / Credential Manager requires a Play Services activity
        // result. When client ID is set, MainActivity / Settings launches the flow
        // via CloudOAuthLauncher; this method is the non-UI guard.
        return SignInResult.Failure(
            "Complete Google Sign-In from Settings (Drive API)."
        )
    }

    override suspend fun downloadSyncFile(context: Context, accessToken: String): String? =
        DriveRest.downloadAppDataFile(accessToken, CloudSyncCodec.FILENAME)

    override suspend fun uploadSyncFile(context: Context, accessToken: String, json: String) {
        DriveRest.uploadAppDataFile(accessToken, CloudSyncCodec.FILENAME, json)
    }
}

class DropboxClient : CloudProviderClient {
    override val provider = CloudSyncPreferences.Provider.DROPBOX
    override fun isConfigured(): Boolean = BuildConfig.DROPBOX_APP_KEY.isNotBlank()

    override suspend fun signIn(context: Context): SignInResult {
        if (!isConfigured()) {
            return SignInResult.Failure(CloudSyncPreferences.NOT_CONFIGURED)
        }
        return SignInResult.Failure("Complete Dropbox Sign-In from Settings.")
    }

    override suspend fun downloadSyncFile(context: Context, accessToken: String): String? =
        DropboxRest.download(accessToken, "/${CloudSyncCodec.FILENAME}")

    override suspend fun uploadSyncFile(context: Context, accessToken: String, json: String) {
        DropboxRest.upload(accessToken, "/${CloudSyncCodec.FILENAME}", json)
    }
}

class OneDriveClient : CloudProviderClient {
    override val provider = CloudSyncPreferences.Provider.ONEDRIVE
    override fun isConfigured(): Boolean = BuildConfig.ONEDRIVE_CLIENT_ID.isNotBlank()

    override suspend fun signIn(context: Context): SignInResult {
        if (!isConfigured()) {
            return SignInResult.Failure(CloudSyncPreferences.NOT_CONFIGURED)
        }
        return SignInResult.Failure("Complete OneDrive Sign-In from Settings.")
    }

    override suspend fun downloadSyncFile(context: Context, accessToken: String): String? =
        GraphRest.downloadAppFolderFile(accessToken, CloudSyncCodec.FILENAME)

    override suspend fun uploadSyncFile(context: Context, accessToken: String, json: String) {
        GraphRest.uploadAppFolderFile(accessToken, CloudSyncCodec.FILENAME, json)
    }
}
