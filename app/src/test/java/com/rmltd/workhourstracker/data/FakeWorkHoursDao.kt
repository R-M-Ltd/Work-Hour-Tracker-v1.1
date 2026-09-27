package com.rmltd.workhourstracker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [WorkHoursDao] for JVM repository tests (no Android Room runtime).
 */
class FakeWorkHoursDao : WorkHoursDao {

    private val entries = LinkedHashMap<Long, DailyEntry>()
    private val weekLogs = LinkedHashMap<Long, WeekLog>()
    private val entriesFlow = MutableStateFlow<List<DailyEntry>>(emptyList())
    private val weekLogsFlow = MutableStateFlow<List<WeekLog>>(emptyList())

    private fun publishEntries() {
        entriesFlow.value = entries.values.sortedBy { it.dateEpochDay }
    }

    private fun publishWeekLogs() {
        weekLogsFlow.value = weekLogs.values.sortedByDescending { it.weekStartEpochDay }
    }

    override suspend fun upsertEntry(entry: DailyEntry) {
        entries[entry.dateEpochDay] = entry
        publishEntries()
    }

    override suspend fun deleteEntry(epochDay: Long) {
        entries.remove(epochDay)
        publishEntries()
    }

    override fun entriesForWeek(startEpochDay: Long, endEpochDay: Long): Flow<List<DailyEntry>> =
        entriesFlow.map { list ->
            list.filter { it.dateEpochDay in startEpochDay..endEpochDay }
        }

    override suspend fun entriesForWeekOnce(startEpochDay: Long, endEpochDay: Long): List<DailyEntry> =
        entries.values.filter { it.dateEpochDay in startEpochDay..endEpochDay }
            .sortedBy { it.dateEpochDay }

    override suspend fun allEntries(): List<DailyEntry> =
        entries.values.sortedBy { it.dateEpochDay }

    override fun entryForDate(epochDay: Long): Flow<DailyEntry?> =
        entriesFlow.map { list -> list.firstOrNull { it.dateEpochDay == epochDay } }

    override suspend fun entryForDateOnce(epochDay: Long): DailyEntry? = entries[epochDay]

    override suspend fun findOpenEntry(): DailyEntry? =
        entries.values
            .filter { it.clockInMinutes != null && it.clockOutMinutes == null }
            .minByOrNull { it.dateEpochDay }

    override fun observeOpenEntry(): Flow<DailyEntry?> =
        entriesFlow.map { list ->
            list.filter { it.clockInMinutes != null && it.clockOutMinutes == null }
                .minByOrNull { it.dateEpochDay }
        }

    override suspend fun insertWeekLog(weekLog: WeekLog): Long {
        if (weekLogs.containsKey(weekLog.weekStartEpochDay)) return -1L
        weekLogs[weekLog.weekStartEpochDay] = weekLog
        publishWeekLogs()
        return weekLog.weekStartEpochDay
    }

    override suspend fun updateWeekLog(
        startEpochDay: Long,
        weekEndEpochDay: Long,
        totalHours: Double,
        archivedAtEpochMillis: Long
    ) {
        val existing = weekLogs[startEpochDay] ?: return
        weekLogs[startEpochDay] = existing.copy(
            weekEndEpochDay = weekEndEpochDay,
            totalHours = totalHours,
            archivedAtEpochMillis = archivedAtEpochMillis
        )
        publishWeekLogs()
    }

    override suspend fun weekLogExists(startEpochDay: Long): Boolean =
        weekLogs.containsKey(startEpochDay)

    override fun allWeekLogs(): Flow<List<WeekLog>> = weekLogsFlow

    override fun allTimeArchivedTotal(): Flow<Double?> =
        weekLogsFlow.map { logs -> logs.sumOf { it.totalHours } }

    override fun allTimeDailyTotal(): Flow<Double?> =
        entriesFlow.map { list -> list.sumOf { it.hoursWorked } }

    override suspend fun deleteAllWeekLogs() {
        weekLogs.clear()
        publishWeekLogs()
    }

    override suspend fun weekStartsWithEntriesBefore(beforeEpochDay: Long): List<Long> =
        entries.values.map { it.weekStartEpochDay }
            .filter { it < beforeEpochDay }
            .distinct()
            .sorted()

    override suspend fun deleteAllEntries() {
        entries.clear()
        publishEntries()
    }

    override suspend fun allWeekLogsOnce(): List<WeekLog> =
        weekLogs.values.sortedBy { it.weekStartEpochDay }

    override suspend fun upsertWeekLog(weekLog: WeekLog) {
        weekLogs[weekLog.weekStartEpochDay] = weekLog
        publishWeekLogs()
    }
}
