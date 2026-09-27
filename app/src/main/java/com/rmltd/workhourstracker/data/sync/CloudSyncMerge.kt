package com.rmltd.workhourstracker.data.sync

import com.rmltd.workhourstracker.data.DailyEntry

/**
 * Last-write-wins merge by [DailyEntry.updatedAtEpochMillis] per dateEpochDay.
 * Tombstones: deleted days carry a deletedAt epoch; if tombstone is newer than
 * the local/remote row, the day is removed.
 */
object CloudSyncMerge {

    data class SyncDay(
        val entry: DailyEntry? = null,
        val deletedAtEpochMillis: Long? = null
    ) {
        val dateEpochDay: Long
            get() = entry?.dateEpochDay
                ?: error("SyncDay needs entry or explicit epoch via map key")
    }

    data class MergeResult(
        val winners: Map<Long, DailyEntry>,
        val deletions: Set<Long>,
        /** Tombstones to persist remotely (epochDay → deletedAt). */
        val tombstones: Map<Long, Long>
    )

    /**
     * @param localEntries current Room rows
     * @param remoteEntries remote snapshot rows
     * @param localTombstones local deletedAt by epochDay
     * @param remoteTombstones remote deletedAt by epochDay
     */
    fun merge(
        localEntries: List<DailyEntry>,
        remoteEntries: List<DailyEntry>,
        localTombstones: Map<Long, Long> = emptyMap(),
        remoteTombstones: Map<Long, Long> = emptyMap()
    ): MergeResult {
        val localBy = localEntries.associateBy { it.dateEpochDay }
        val remoteBy = remoteEntries.associateBy { it.dateEpochDay }
        val allDays = localBy.keys + remoteBy.keys + localTombstones.keys + remoteTombstones.keys

        val winners = linkedMapOf<Long, DailyEntry>()
        val deletions = linkedSetOf<Long>()
        val tombstones = linkedMapOf<Long, Long>()

        for (day in allDays) {
            val local = localBy[day]
            val remote = remoteBy[day]
            val localT = localTombstones[day]
            val remoteT = remoteTombstones[day]
            val bestTombstone = listOfNotNull(localT, remoteT).maxOrNull()

            val bestEntry = when {
                local == null && remote == null -> null
                local == null -> remote
                remote == null -> local
                local.updatedAtEpochMillis >= remote.updatedAtEpochMillis -> local
                else -> remote
            }

            if (bestTombstone != null &&
                (bestEntry == null || bestTombstone >= bestEntry.updatedAtEpochMillis)
            ) {
                deletions.add(day)
                tombstones[day] = bestTombstone
            } else if (bestEntry != null) {
                winners[day] = bestEntry
            }
        }
        return MergeResult(winners, deletions, tombstones)
    }
}
