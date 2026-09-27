# Cloud sync (1.3.35)

Optional bidirectional sync of `DailyEntry` rows (including `hoursSource`, `noLunchTaken`)
to **one** of: Google Drive, Dropbox, OneDrive. Default **off**. iCloud is not available
on Android.

## OAuth (authorization code + PKCE + refresh)

Sign-in uses `response_type=code` with PKCE. Access + refresh tokens are stored in
EncryptedSharedPreferences. Sync refreshes the access token before Sync now / on-resume
and retries once on HTTP 401. If refresh fails, Settings shows **Session expired — sign
in again to keep syncing.** with **Sign in again** (Sync now disabled until re-auth).

## local.properties keys (gitignored — never commit)

```
DRIVE_CLIENT_ID=your-google-oauth-client-id.apps.googleusercontent.com
DROPBOX_APP_KEY=your-dropbox-app-key
ONEDRIVE_CLIENT_ID=your-azure-app-client-id
```

Rebuild after adding keys so `BuildConfig` picks them up.

## Redirect URI

Register with each provider console:

```
com.rmltd.workhourstracker://oauth
```

## Behavior without keys

Sign in shows **“Cloud provider not configured”** — no crash, no fake linked state.
LWW merge + prefs defaults are unit-tested without live OAuth.

## Conflict policy

If the same day changed on two devices, the latest save wins (`updatedAtEpochMillis`).
