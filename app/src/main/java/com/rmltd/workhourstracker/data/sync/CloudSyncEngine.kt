package com.rmltd.workhourstracker.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.rmltd.workhourstracker.data.CloudSyncPreferences
import com.rmltd.workhourstracker.data.WorkHoursRepository
import org.json.JSONObject

/**
 * Bidirectional sync of DailyEntry (+ hoursSource, noLunchTaken).
 * LWW by updatedAtEpochMillis. No network work when sync is off.
 * 1.3.35: refreshes access token before Sync / on-resume / on 401.
 */
class CloudSyncEngine(
    private val repository: WorkHoursRepository
) {

    sealed class SyncOutcome {
        data object SkippedOff : SyncOutcome()
        data object SkippedNotLinked : SyncOutcome()
        data object SkippedNoNetwork : SyncOutcome()
        data object SessionExpired : SyncOutcome()
        data class Success(val mergedDays: Int) : SyncOutcome()
        data class Error(val message: String) : SyncOutcome()
    }

    suspend fun syncNow(context: Context): SyncOutcome {
        if (!CloudSyncPreferences.isEnabled(context)) return SyncOutcome.SkippedOff
        if (CloudSyncPreferences.isSessionExpired(context)) {
            CloudSyncPreferences.setLastError(context, CloudSyncPreferences.SESSION_EXPIRED)
            return SyncOutcome.SessionExpired
        }
        if (!CloudSyncPreferences.isLinked(context)) return SyncOutcome.SkippedNotLinked
        if (!hasNetwork(context)) return SyncOutcome.SkippedNoNetwork

        val provider = CloudSyncPreferences.getProvider(context)
            ?: return SyncOutcome.SkippedNotLinked

        val token = ensureFreshAccessToken(context, provider)
            ?: run {
                CloudSyncPreferences.markSessionExpired(context)
                return SyncOutcome.SessionExpired
            }

        return try {
            runSyncBody(context, provider, token)
        } catch (e: CloudAuthExpiredException) {
            val refreshed = refreshAccessToken(context, provider)
            if (refreshed == null) {
                CloudSyncPreferences.markSessionExpired(context)
                SyncOutcome.SessionExpired
            } else {
                try {
                    runSyncBody(context, provider, refreshed)
                } catch (e2: Exception) {
                    val msg = e2.message ?: "Sync failed"
                    if (e2 is CloudAuthExpiredException) {
                        CloudSyncPreferences.markSessionExpired(context)
                        SyncOutcome.SessionExpired
                    } else {
                        CloudSyncPreferences.setLastError(context, msg)
                        SyncOutcome.Error(msg)
                    }
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: "Sync failed"
            CloudSyncPreferences.setLastError(context, msg)
            SyncOutcome.Error(msg)
        }
    }

    private suspend fun runSyncBody(
        context: Context,
        provider: CloudSyncPreferences.Provider,
        token: String
    ): SyncOutcome {
        val client = CloudProviderFactory.clientFor(provider)
        val remoteJson = client.downloadSyncFile(context, token)
        val remote = if (remoteJson.isNullOrBlank()) {
            CloudSyncCodec.Snapshot(emptyList(), emptyMap())
        } else {
            CloudSyncCodec.decode(remoteJson)
        }
        val localEntries = repository.allEntriesOnce()
        val localTombs = readTombstones(context)
        val merged = CloudSyncMerge.merge(
            localEntries = localEntries,
            remoteEntries = remote.entries,
            localTombstones = localTombs,
            remoteTombstones = remote.tombstones
        )
        for (day in merged.deletions) {
            repository.deleteEntryFromSync(day)
        }
        for (entry in merged.winners.values) {
            val existing = localEntries.firstOrNull { it.dateEpochDay == entry.dateEpochDay }
            if (existing == null ||
                entry.updatedAtEpochMillis != existing.updatedAtEpochMillis ||
                entry != existing
            ) {
                repository.upsertEntryFromSync(entry)
            }
        }
        writeTombstones(context, merged.tombstones)
        val outJson = CloudSyncCodec.encode(
            entries = merged.winners.values.toList(),
            tombstones = merged.tombstones
        )
        client.uploadSyncFile(context, token, outJson)
        CloudSyncPreferences.setLastSyncEpochMillis(context, System.currentTimeMillis())
        CloudSyncPreferences.clearLastError(context)
        CloudSyncPreferences.setSessionExpired(context, false)
        return SyncOutcome.Success(merged.winners.size)
    }

    /** Resume trigger: only when enabled + linked + network. Refreshes token first. */
    suspend fun syncOnResumeIfNeeded(context: Context): SyncOutcome {
        if (!CloudSyncPreferences.isEnabled(context)) return SyncOutcome.SkippedOff
        if (CloudSyncPreferences.isSessionExpired(context)) return SyncOutcome.SessionExpired
        if (!CloudSyncPreferences.isLinked(context)) return SyncOutcome.SkippedNotLinked
        return syncNow(context)
    }

    /**
     * Returns a usable access token, refreshing when expired / missing expiry buffer.
     * Null → caller should mark session expired.
     */
    fun ensureFreshAccessToken(
        context: Context,
        provider: CloudSyncPreferences.Provider
    ): String? {
        val access = CloudSyncPreferences.getAccessToken(context)
        val expiresAt = CloudSyncPreferences.getAccessExpiresAt(context)
        val skewMs = 60_000L
        val stillValid = !access.isNullOrBlank() &&
            (expiresAt <= 0L || System.currentTimeMillis() < expiresAt - skewMs)
        if (stillValid) return access
        return refreshAccessToken(context, provider) ?: access?.takeIf {
            // No refresh token but we still have access — try it (expiresAt unknown).
            CloudSyncPreferences.getRefreshToken(context).isNullOrBlank() && expiresAt <= 0L
        }
    }

    fun refreshAccessToken(
        context: Context,
        provider: CloudSyncPreferences.Provider
    ): String? {
        val refresh = CloudSyncPreferences.getRefreshToken(context) ?: return null
        return try {
            val tokens = OAuthTokenExchange.refresh(provider, refresh)
            val expiresAt = if (tokens.expiresInSec > 0) {
                System.currentTimeMillis() + tokens.expiresInSec * 1000L
            } else 0L
            val newRefresh = tokens.refreshToken ?: refresh
            CloudSyncPreferences.updateAccessToken(
                context,
                tokens.accessToken,
                newRefresh,
                expiresAt
            )
            // Ensure refresh persisted even if updateAccessToken got same value
            if (CloudSyncPreferences.getRefreshToken(context).isNullOrBlank()) {
                CloudSyncPreferences.setTokens(context, tokens.accessToken, newRefresh)
            }
            tokens.accessToken
        } catch (_: Exception) {
            null
        }
    }

    private fun readTombstones(context: Context): Map<Long, Long> {
        val raw = CloudSyncPreferences.getTombstonesJson(context)
        return try {
            val o = JSONObject(raw)
            val map = mutableMapOf<Long, Long>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k.toLong()] = o.getLong(k)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeTombstones(context: Context, tombs: Map<Long, Long>) {
        val o = JSONObject()
        for ((k, v) in tombs) o.put(k.toString(), v)
        CloudSyncPreferences.setTombstonesJson(context, o.toString())
    }

    private fun hasNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
