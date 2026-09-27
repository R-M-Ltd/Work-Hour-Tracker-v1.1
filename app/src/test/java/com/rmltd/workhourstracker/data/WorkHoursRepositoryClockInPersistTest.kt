package com.rmltd.workhourstracker.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Regression: Clock in now must leave an open Room row that a fresh read still sees
 * (force-stop / cold start equivalent = new repository read against same dao).
 */
class WorkHoursRepositoryClockInPersistTest {

    private val today = LocalDate.of(2026, 9, 26)
    private val nineAm = 9 * 60
    private val tenAm = 10 * 60

    private fun newRepo(dao: FakeWorkHoursDao = FakeWorkHoursDao()) =
        dao to WorkHoursRepository(dao) { DayOfWeek.WEDNESDAY }

    @Test
    fun clockInNow_persistsOpenPunch_readableAfterFreshLookup() = runBlocking {
        val (dao, repo) = newRepo()
        val result = repo.clockInNow(today, nineAm)
        assertEquals(ClockInResult.STARTED, result)

        // Simulate process death: drop the repository, keep the dao (Room file).
        val repo2 = WorkHoursRepository(dao) { DayOfWeek.WEDNESDAY }
        val loaded = repo2.entryForDateOnce(today)
        assertNotNull(loaded)
        assertEquals(nineAm, loaded!!.clockInMinutes)
        assertNull(loaded.clockOutMinutes)
        assertEquals(0.0, loaded.hoursWorked, 0.0)
        assertEquals(ClockDayState.Kind.OPEN, ClockDayState.classify(
            loaded.clockInMinutes, loaded.clockOutMinutes, loaded.hoursWorked
        ))
    }

    @Test
    fun updateOpenClockIn_changesMinutes_survivesReread() = runBlocking {
        val (dao, repo) = newRepo()
        assertEquals(ClockInResult.STARTED, repo.clockInNow(today, nineAm))
        assertTrue(repo.updateOpenClockIn(today, tenAm))

        val loaded = WorkHoursRepository(dao) { DayOfWeek.WEDNESDAY }.entryForDateOnce(today)
        assertEquals(tenAm, loaded!!.clockInMinutes)
        assertNull(loaded.clockOutMinutes)
    }

    @Test
    fun clockInNow_preservesExistingNoteOnOpenPunch() = runBlocking {
        val (dao, repo) = newRepo()
        // Seed a hours-only empty-ish row with a note but no clocks (EMPTY kind via 0h).
        dao.upsertEntry(
            DailyEntry(
                dateEpochDay = today.toEpochDay(),
                hoursWorked = 0.0,
                comments = "pre-note",
                weekStartEpochDay = today.toEpochDay()
            )
        )
        assertEquals(ClockInResult.STARTED, repo.clockInNow(today, nineAm))
        val loaded = repo.entryForDateOnce(today)!!
        assertEquals("pre-note", loaded.comments)
        assertEquals(nineAm, loaded.clockInMinutes)
    }

    @Test
    fun clockInNow_atomicComments_writtenWithOpenPunch() = runBlocking {
        val (dao, repo) = newRepo()
        val note = "12:00 AM (Clock in): Overnight start"
        assertEquals(
            ClockInResult.STARTED,
            repo.clockInNow(today, 0, comments = note)
        )
        val loaded = repo.entryForDateOnce(today)!!
        assertEquals(0, loaded.clockInMinutes)
        assertEquals(note, loaded.comments)
        assertNull(loaded.clockOutMinutes)
    }

    @Test
    fun updateOpenClockIn_atomicComments_sameUpsert() = runBlocking {
        val (_, repo) = newRepo()
        assertEquals(ClockInResult.STARTED, repo.clockInNow(today, nineAm))
        val note = "12:00 AM (Clock in): Moved to midnight"
        assertTrue(repo.updateOpenClockIn(today, 0, comments = note))
        val loaded = repo.entryForDateOnce(today)!!
        assertEquals(0, loaded.clockInMinutes)
        assertEquals(note, loaded.comments)
    }

    @Test
    fun findOpenEntry_picksOldestWhenMultipleOpen() = runBlocking {
        val (dao, repo) = newRepo()
        val older = today.minusDays(3)
        val newer = today.minusDays(1)
        dao.upsertEntry(
            DailyEntry(
                dateEpochDay = older.toEpochDay(),
                hoursWorked = 0.0,
                comments = "",
                weekStartEpochDay = older.toEpochDay(),
                clockInMinutes = nineAm,
                clockOutMinutes = null
            )
        )
        dao.upsertEntry(
            DailyEntry(
                dateEpochDay = newer.toEpochDay(),
                hoursWorked = 0.0,
                comments = "",
                weekStartEpochDay = newer.toEpochDay(),
                clockInMinutes = tenAm,
                clockOutMinutes = null
            )
        )
        val open = repo.findOpenEntryOnce()
        assertNotNull(open)
        assertEquals(older.toEpochDay(), open!!.dateEpochDay)
    }

    @Test
    fun clockOutNow_overnight_closesAllCorruptMultiOpen() = runBlocking {
        val (dao, repo) = newRepo()
        val older = today.minusDays(3)
        val yesterday = today.minusDays(1)
        val outAt = 9 * 60
        dao.upsertEntry(
            DailyEntry(
                dateEpochDay = older.toEpochDay(),
                hoursWorked = 0.0,
                comments = "",
                weekStartEpochDay = older.toEpochDay(),
                clockInMinutes = nineAm,
                clockOutMinutes = null
            )
        )
        dao.upsertEntry(
            DailyEntry(
                dateEpochDay = yesterday.toEpochDay(),
                hoursWorked = 0.0,
                comments = "",
                weekStartEpochDay = yesterday.toEpochDay(),
                clockInMinutes = tenAm,
                clockOutMinutes = null
            )
        )
        assertEquals(ClockOutResult.SUCCESS_OVERNIGHT, repo.clockOutNow(today, outAt))
        // Prefer yesterday overnight close, then drain remaining corrupt opens (S-C).
        val y = repo.entryForDateOnce(yesterday)!!
        assertEquals(outAt, y.clockOutMinutes)
        val o = repo.entryForDateOnce(older)!!
        assertEquals(outAt, o.clockOutMinutes)
        assertNull(repo.findOpenEntryOnce())
    }

    @Test
    fun clockOutNow_success_drainsLeftoverNonTodayOpens() = runBlocking {
        // D7: same-day SUCCESS must also drain leftover non-today opens (silent).
        val (dao, repo) = newRepo()
        val older = today.minusDays(3)
        val outAt = 17 * 60
        dao.upsertEntry(
            DailyEntry(
                dateEpochDay = older.toEpochDay(),
                hoursWorked = 0.0,
                comments = "",
                weekStartEpochDay = older.toEpochDay(),
                clockInMinutes = nineAm,
                clockOutMinutes = null
            )
        )
        dao.upsertEntry(
            DailyEntry(
                dateEpochDay = today.toEpochDay(),
                hoursWorked = 0.0,
                comments = "",
                weekStartEpochDay = today.toEpochDay(),
                clockInMinutes = nineAm,
                clockOutMinutes = null
            )
        )
        assertEquals(ClockOutResult.SUCCESS, repo.clockOutNow(today, outAt))
        val t = repo.entryForDateOnce(today)!!
        assertEquals(outAt, t.clockOutMinutes)
        val o = repo.entryForDateOnce(older)!!
        assertEquals(outAt, o.clockOutMinutes)
        assertNull(repo.findOpenEntryOnce())
    }
}
