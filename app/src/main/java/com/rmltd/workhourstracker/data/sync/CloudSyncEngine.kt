package com.rmltd.workhourstracker.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.rmltd.workhourstracker.data.CloudSyncPreferences
import com.rmltd.workhourstracker.data.DailyEntry
import com.rmltd.workhourstracker.data.WorkHoursRepository
import org.json.JSONObject

/**
 * Bidirectional sync of DailyEntry (+ hoursSource, noLunchTaken).
 * LWW by updatedAtEpochMillis. No network work when sync is off.
 */
class CloudSyncEngine(
    private val repository: WorkHoursRepository
) {

    sealed class SyncOutcome {
        data object SkippedOff : SyncOutcome()
        data object SkippedNotLinked : SyncOutcome()
        data object SkippedNoNetwork : SyncOutcome()
        data class Success(val mergedDays: Int) : SyncOutcome()
        data class Error(val message: String) : SyncOutcome()
    }

    suspend fun syncNow(context: Context): SyncOutcome {
        if (!CloudSyncPreferences.isEnabled(context)) return SyncOutcome.SkippedOff
        if (!CloudSyncPreferences.isLinked(context)) return SyncOutcome.SkippedNotLinked
        if (!hasNetwork(context)) return SyncOutcome.SkippedNoNetwork

        val provider = CloudSyncPreferences.getProvider(context)
            ?: return SyncOutcome.SkippedNotLinked
        val token = CloudSyncPreferences.getAccessToken(context)
            ?: return SyncOutcome.SkippedNotLinked
        val client = CloudProviderFactory.clientFor(provider)

        return try {
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
            SyncOutcome.Success(merged.winners.size)
        } catch (e: Exception) {
            val msg = e.message ?: "Sync failed"
            CloudSyncPreferences.setLastError(context, msg)
            SyncOutcome.Error(msg)
        }
    }

    /** Resume trigger: only when enabled + linked + network. */
    suspend fun syncOnResumeIfNeeded(context: Context): SyncOutcome {
        if (!CloudSyncPreferences.isEnabled(context)) return SyncOutcome.SkippedOff
        if (!CloudSyncPreferences.isLinked(context)) return SyncOutcome.SkippedNotLinked
        return syncNow(context)
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
