package com.rmltd.workhourstracker.util

import java.time.LocalDate

/**
 * Pure Home overnight toast / helper copy.
 * Open punches may be yesterday or an older gap-day orphan — never hard-code "yesterday"
 * when [openOvernightDate] is older (matches Home overnight dialog branching).
 */
object HomeOvernightCopy {

    /**
     * Toast after [com.rmltd.workhourstracker.data.ClockOutResult.SUCCESS_OVERNIGHT]
     * from the Home "Clock out now" button.
     */
    fun clockOutOvernightToast(openOvernightDate: LocalDate?, today: LocalDate): String {
        val openDay = openOvernightDate ?: today.minusDays(1)
        return if (openDay == today.minusDays(1)) {
            "Finished yesterday's overnight shift"
        } else {
            "Finished open overnight shift"
        }
    }

    /**
     * Helper under "Clock out now". Uses neutral "open overnight" when the open day
     * is older than yesterday; keeps "yesterday's overnight" only when it actually is.
     */
    fun clockOutHelper(overnightPending: Boolean, openOvernightDate: LocalDate?, today: LocalDate): String {
        if (!overnightPending) {
            return "Sets time to now. Break/lunch: edit the day."
        }
        val openDay = openOvernightDate ?: today.minusDays(1)
        val overnightPhrase =
            if (openDay == today.minusDays(1)) "yesterday's overnight"
            else "the open overnight"
        return "Sets time to now (or finishes $overnightPhrase). Break/lunch: edit the day."
    }
}
