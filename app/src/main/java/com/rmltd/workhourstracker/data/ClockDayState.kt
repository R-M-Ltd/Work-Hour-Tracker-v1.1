package com.rmltd.workhourstracker.data

import java.time.LocalDate

/**
 * Pure day-state classification and Home clock transition decisions.
 * No Room, mutex, or upsert — inputs are nullable minutes / hours; outputs
 * match [ClockInResult], [ClockOutResult], and [HomeClockUi].
 */
object ClockDayState {

    /** Single calendar day's punch classification. */
    enum class Kind {
        EMPTY,
        OPEN,
        CLOSED,
        LEGACY_CLOSED
    }

    fun classify(
        clockInMinutes: Int?,
        clockOutMinutes: Int?,
        hoursWorked: Double = 0.0
    ): Kind {
        if (clockInMinutes != null && clockOutMinutes != null) return Kind.CLOSED
        if (clockInMinutes != null) return Kind.OPEN
        // Migrated hours-only row (no clocks): treat as closed — do not wipe.
        if (hoursWorked > 0.0) return Kind.LEGACY_CLOSED
        return Kind.EMPTY
    }

    /** Open punch: in set, out null (overnight, same-day open, or orphan older day). */
    fun isOvernightOpen(clockInMinutes: Int?, clockOutMinutes: Int?): Boolean =
        clockInMinutes != null && clockOutMinutes == null

    /**
     * What [WorkHoursRepository.clockInNow] should return given today's and
     * yesterday's punch fields (before any write).
     *
     * Empty → STARTED (caller upserts). Open/Closed/legacy → no-op results.
     * Empty + yesterday overnight open → BLOCKED_OVERNIGHT.
     * Empty + [orphanOpenOnOtherDay] (open on a day older than yesterday) →
     * BLOCKED_OVERNIGHT — never STARTED while an older open remains.
     */
    fun decideClockIn(
        todayIn: Int?,
        todayOut: Int?,
        todayHoursWorked: Double,
        yesterdayIn: Int?,
        yesterdayOut: Int?,
        orphanOpenOnOtherDay: Boolean = false
    ): ClockInResult {
        when (classify(todayIn, todayOut, todayHoursWorked)) {
            Kind.CLOSED, Kind.LEGACY_CLOSED -> return ClockInResult.ALREADY_CLOSED
            Kind.OPEN -> return ClockInResult.ALREADY_OPEN
            Kind.EMPTY -> Unit
        }
        if (isOvernightOpen(yesterdayIn, yesterdayOut) || orphanOpenOnOtherDay) {
            return ClockInResult.BLOCKED_OVERNIGHT
        }
        return ClockInResult.STARTED
    }

    /**
     * What [WorkHoursRepository.clockOutNow] should return given punches and
     * intended out minutes (before any write).
     *
     * Open today → SUCCESS (caller closes today; equal wall times → FAILED).
     * Empty today + yesterday overnight → SUCCESS_OVERNIGHT (caller closes yesterday).
     * Empty today + [orphanOpenOnOtherDay] → SUCCESS_OVERNIGHT (caller closes that day).
     * Closed today → ALREADY_CLOSED. Else FAILED.
     */
    fun decideClockOut(
        todayIn: Int?,
        todayOut: Int?,
        yesterdayIn: Int?,
        yesterdayOut: Int?,
        outMinutes: Int,
        orphanOpenOnOtherDay: Boolean = false
    ): ClockOutResult {
        if (todayIn != null && todayOut != null) {
            return ClockOutResult.ALREADY_CLOSED
        }
        if (todayIn != null) {
            // Same calendar day: still reject identical wall times (0h).
            if (todayIn == outMinutes) return ClockOutResult.FAILED
            return ClockOutResult.SUCCESS
        }
        if (isOvernightOpen(yesterdayIn, yesterdayOut) || orphanOpenOnOtherDay) {
            return ClockOutResult.SUCCESS_OVERNIGHT
        }
        return ClockOutResult.FAILED
    }

    /**
     * Home clock button / overnight UI from punch fields.
     * Overnight-pending keeps clock-in enabled so Home can show the resolve dialog.
     * [orphanOpenEpochDay] surfaces an open punch older than yesterday.
     */
    fun deriveHomeClockUi(
        todayIn: Int?,
        todayOut: Int?,
        todayHoursWorked: Double,
        yesterdayIn: Int?,
        yesterdayOut: Int?,
        yesterdayEpochDay: Long? = null,
        orphanOpenEpochDay: Long? = null
    ): HomeClockUi {
        val yesterdayOvernight = isOvernightOpen(yesterdayIn, yesterdayOut)
        val overnightPending = yesterdayOvernight || orphanOpenEpochDay != null
        val todayKind = classify(todayIn, todayOut, todayHoursWorked)
        val todayOpen = todayKind == Kind.OPEN
        val todayClosed = todayKind == Kind.CLOSED || todayKind == Kind.LEGACY_CLOSED

        val clockInEnabled = !todayOpen && !todayClosed
        val clockOutEnabled = todayOpen || overnightPending

        val openDate = when {
            yesterdayOvernight && yesterdayEpochDay != null ->
                LocalDate.ofEpochDay(yesterdayEpochDay)
            orphanOpenEpochDay != null ->
                LocalDate.ofEpochDay(orphanOpenEpochDay)
            else -> null
        }

        return HomeClockUi(
            clockInEnabled = clockInEnabled,
            clockOutEnabled = clockOutEnabled,
            overnightPending = overnightPending,
            openOvernightDate = openDate
        )
    }

    /** Convenience over [DailyEntry] rows (Repository Flow / tests). */
    fun deriveHomeClockUi(
        todayEntry: DailyEntry?,
        yesterdayEntry: DailyEntry?,
        orphanOpenEntry: DailyEntry? = null
    ): HomeClockUi {
        val todayEpoch = todayEntry?.dateEpochDay
        val yesterdayEpoch = yesterdayEntry?.dateEpochDay
            ?: todayEpoch?.let { it - 1 }
        val orphanEpoch = orphanOpenEntry?.dateEpochDay?.takeIf { epoch ->
            epoch != todayEpoch && epoch != yesterdayEpoch
        }
        return deriveHomeClockUi(
            todayIn = todayEntry?.clockInMinutes,
            todayOut = todayEntry?.clockOutMinutes,
            todayHoursWorked = todayEntry?.hoursWorked ?: 0.0,
            yesterdayIn = yesterdayEntry?.clockInMinutes,
            yesterdayOut = yesterdayEntry?.clockOutMinutes,
            yesterdayEpochDay = yesterdayEntry?.dateEpochDay,
            orphanOpenEpochDay = orphanEpoch
        )
    }
}
