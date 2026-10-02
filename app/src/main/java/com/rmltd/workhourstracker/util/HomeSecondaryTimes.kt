package com.rmltd.workhourstracker.util

/**
 * Quiet secondary Start · Stop / since chips on Home (1.3.42).
 * Pure helpers for visibility + labels — never peer weight to Clock-in / elapsed.
 */
object HomeSecondaryTimes {

    /**
     * Show chips when OPEN (running/paused), or idle-with-partial times
     * (CLOSED clocks or LOCAL_ONLY draft). Pure EMPTY idle omits chips.
     */
    fun shouldShow(
        openSession: Boolean,
        overnightPending: Boolean,
        inMinutes: Int?,
        outMinutes: Int?
    ): Boolean {
        if (overnightPending) return false
        if (openSession) return true
        return inMinutes != null || outMinutes != null
    }

    /** Running/Paused: "Since {in}"; Idle-partial: "Start {in|—}". */
    fun startChipLabel(openSession: Boolean, inMinutes: Int?): String {
        val value = inMinutes?.let { HoursCalc.formatClock(it) } ?: "—"
        return if (openSession) "Since $value" else "Start $value"
    }

    /** Running/Paused open: "Stop —"; otherwise Stop with out or em dash. */
    fun stopChipLabel(openSession: Boolean, outMinutes: Int?): String {
        if (openSession) return "Stop —"
        val value = outMinutes?.let { HoursCalc.formatClock(it) } ?: "—"
        return "Stop $value"
    }

    /**
     * Widget status line (keep paused freeze copy from 1.3.41).
     * Running → Start {in} · Stop —; CLOSED → Start {in} · Stop {out}.
     */
    fun widgetStatusLine(
        open: Boolean,
        paused: Boolean,
        inMinutes: Int?,
        outMinutes: Int?
    ): String? {
        if (inMinutes == null) return null
        return when {
            open && paused -> "Paused · since ${HoursCalc.formatClock(inMinutes)}"
            open -> "Start ${HoursCalc.formatClock(inMinutes)} · Stop —"
            outMinutes != null ->
                "Start ${HoursCalc.formatClock(inMinutes)} · Stop ${HoursCalc.formatClock(outMinutes)}"
            else -> null
        }
    }
}
