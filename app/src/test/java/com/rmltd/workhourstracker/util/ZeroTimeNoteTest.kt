package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZeroTimeNoteTest {

    @Test
    fun needsReason_onlyExactMidnight() {
        assertTrue(ZeroTimeNote.needsReason(0))
        assertFalse(ZeroTimeNote.needsReason(null))
        assertFalse(ZeroTimeNote.needsReason(1))
        assertFalse(ZeroTimeNote.needsReason(12 * 60))
    }

    @Test
    fun dialogBody_includesFieldLabel() {
        assertTrue(ZeroTimeNote.dialogBody("Clock in").contains("Clock in"))
        assertTrue(ZeroTimeNote.dialogBody("Break start").contains("12:00 AM"))
    }

    @Test
    fun formatAndMerge_packAppendFormat() {
        assertEquals(
            "12:00 AM (Clock in): Overnight start",
            ZeroTimeNote.formatReasonLine("Clock in", "Overnight start")
        )
        assertEquals(
            "12:00 AM (Clock in): Overnight start",
            ZeroTimeNote.mergeReasonIntoNote("", "Clock in", "Overnight start")
        )
        assertEquals(
            "Existing\n12:00 AM (Clock out): Left at midnight",
            ZeroTimeNote.mergeReasonIntoNote("Existing", "Clock out", "Left at midnight")
        )
        // Cancel path never merges blank
        assertEquals("keep", ZeroTimeNote.mergeReasonIntoNote("keep", "Clock in", "  "))
    }

    @Test
    fun mergeReasonIntoNote_dedupesOnlyFullLine_notBareReasonSubstring() {
        val existing = "Site opened early for delivery"
        // Bare reason "early" must NOT suppress appending the formatted midnight line.
        val merged = ZeroTimeNote.mergeReasonIntoNote(existing, "Clock in", "early")
        assertEquals(
            "Site opened early for delivery\n12:00 AM (Clock in): early",
            merged
        )
        // Full line already present → no second append
        assertEquals(
            merged,
            ZeroTimeNote.mergeReasonIntoNote(merged, "Clock in", "early")
        )
    }

    @Test
    fun mergeReasonIntoNote_dedupesExactLineEquality_notPrefixSubstring() {
        // Longer line that contains the shorter formatted line as a prefix must NOT
        // suppress a distinct append (D2 — was String.contains).
        val existing = "12:00 AM (Clock in): early bird"
        val merged = ZeroTimeNote.mergeReasonIntoNote(existing, "Clock in", "early")
        assertEquals(
            "12:00 AM (Clock in): early bird\n12:00 AM (Clock in): early",
            merged
        )
        // Exact full line present → no second append
        assertEquals(
            merged,
            ZeroTimeNote.mergeReasonIntoNote(merged, "Clock in", "early")
        )
    }

    @Test
    fun containsFullLine_equalityOnly() {
        assertTrue(
            ZeroTimeNote.containsFullLine(
                "12:00 AM (Clock in): early bird",
                "12:00 AM (Clock in): early bird"
            )
        )
        assertFalse(
            ZeroTimeNote.containsFullLine(
                "12:00 AM (Clock in): early bird",
                "12:00 AM (Clock in): early"
            )
        )
        assertTrue(
            ZeroTimeNote.containsFullLine(
                "Note\n12:00 AM (Clock in): early\nMore",
                "12:00 AM (Clock in): early"
            )
        )
    }

    @Test
    fun mergeZeroHoursIntoNote_formatAndDedupe() {
        assertEquals(
            "0 hours: unpaid training day",
            ZeroTimeNote.mergeZeroHoursIntoNote("", "unpaid training day")
        )
        assertEquals(
            "Note\n0 hours: unpaid training day",
            ZeroTimeNote.mergeZeroHoursIntoNote("Note", "unpaid training day")
        )
        val once = ZeroTimeNote.mergeZeroHoursIntoNote("Note", "unpaid training day")
        assertEquals(once, ZeroTimeNote.mergeZeroHoursIntoNote(once, "unpaid training day"))
        // Prefix of a longer zero-hours line must not suppress a distinct reason.
        val longer = "0 hours: unpaid training day off"
        assertEquals(
            "0 hours: unpaid training day off\n0 hours: unpaid training day",
            ZeroTimeNote.mergeZeroHoursIntoNote(longer, "unpaid training day")
        )
    }

    @Test
    fun canSaveWithNote_requiresNonBlankWhenMidnightPresent() {
        assertTrue(ZeroTimeNote.canSaveWithNote("", 480, 1020))
        assertFalse(ZeroTimeNote.canSaveWithNote("", 0, 1020))
        assertTrue(ZeroTimeNote.canSaveWithNote("Started at midnight", 0, 1020))
    }

    @Test
    fun needsZeroHoursReason_secondaryGate() {
        // Positive hours, in≠out → no gate
        assertFalse(ZeroTimeNote.needsZeroHoursReason(480, 1020, 8.0))
        // Break-eats-shift: in≠out but hours 0 → gate
        assertTrue(ZeroTimeNote.needsZeroHoursReason(480, 1020, 0.0))
        // 1.3.33: equal times → gate (intentional 0.00h day), even if hours null
        assertTrue(ZeroTimeNote.needsZeroHoursReason(480, 480, 0.0))
        assertTrue(ZeroTimeNote.needsZeroHoursReason(480, 480, null))
        assertTrue(ZeroTimeNote.needsZeroHoursReason(0, 0, 0.0))
        assertFalse(ZeroTimeNote.needsZeroHoursReason(null, 1020, 0.0))
        assertFalse(ZeroTimeNote.needsZeroHoursReason(480, null, 0.0))
    }

    @Test
    fun zeroHoursSaveCaption_packCopy() {
        assertEquals(
            "0.00h — Save will ask for a short reason",
            ZeroTimeNote.ZERO_HOURS_SAVE_CAPTION
        )
    }

    @Test
    fun unsavedDraftCaption_packCopy() {
        assertEquals(
            "Not saved yet — tap Save today's times",
            ZeroTimeNote.UNSAVED_DRAFT_CAPTION
        )
    }

    @Test
    fun restoredUnsavedTimesToast_packCopy() {
        assertEquals(
            "Restored unsaved times",
            ZeroTimeNote.RESTORED_UNSAVED_TIMES_TOAST
        )
    }
}
