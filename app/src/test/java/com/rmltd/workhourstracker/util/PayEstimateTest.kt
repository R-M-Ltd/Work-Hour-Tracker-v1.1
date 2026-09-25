package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayEstimateTest {

    @Test
    fun isOvertime_falseWhenUnderOrEqualGoal() {
        assertFalse(PayEstimate.isOvertime(39.99, 40.0))
        assertFalse(PayEstimate.isOvertime(40.0, 40.0))
        assertFalse(PayEstimate.isOvertime(10.0, 0.0))
        assertFalse(PayEstimate.isOvertime(50.0, -1.0))
    }

    @Test
    fun isOvertime_trueWhenPastGoal() {
        assertTrue(PayEstimate.isOvertime(40.01, 40.0))
        assertTrue(PayEstimate.isOvertime(48.0, 40.0))
    }

    @Test
    fun overtimeHours_zeroWhenNotOver() {
        assertEquals(0.0, PayEstimate.overtimeHours(40.0, 40.0), 0.001)
        assertEquals(0.0, PayEstimate.overtimeHours(20.0, 40.0), 0.001)
        assertEquals(0.0, PayEstimate.overtimeHours(50.0, 0.0), 0.001)
    }

    @Test
    fun overtimeHours_differenceWhenOver() {
        assertEquals(2.5, PayEstimate.overtimeHours(42.5, 40.0), 0.001)
        assertEquals(0.01, PayEstimate.overtimeHours(40.01, 40.0), 0.001)
    }

    @Test
    fun remainingHours_clampedAtZero() {
        assertEquals(5.0, PayEstimate.remainingHours(35.0, 40.0), 0.001)
        assertEquals(0.0, PayEstimate.remainingHours(45.0, 40.0), 0.001)
        assertEquals(0.0, PayEstimate.remainingHours(10.0, 0.0), 0.001)
    }

    @Test
    fun progressFraction_capsAtOne() {
        assertEquals(0.5f, PayEstimate.progressFraction(20.0, 40.0), 0.001f)
        assertEquals(1.0f, PayEstimate.progressFraction(40.0, 40.0), 0.001f)
        assertEquals(1.0f, PayEstimate.progressFraction(50.0, 40.0), 0.001f)
        assertEquals(0f, PayEstimate.progressFraction(10.0, 0.0), 0.001f)
    }

    @Test
    fun roughPay_nullWhenRateUnsetOrZero() {
        assertNull(PayEstimate.roughPay(40.0, 0.0))
        assertNull(PayEstimate.roughPay(40.0, -5.0))
    }

    @Test
    fun roughPay_hoursTimesRate() {
        assertEquals(600.0, PayEstimate.roughPay(40.0, 15.0)!!, 0.001)
        assertEquals(337.50, PayEstimate.roughPay(22.5, 15.0)!!, 0.001)
        assertEquals(0.0, PayEstimate.roughPay(0.0, 20.0)!!, 0.001)
    }

    @Test
    fun roughPay_negativeHoursTreatedAsZero() {
        assertEquals(0.0, PayEstimate.roughPay(-5.0, 20.0)!!, 0.001)
    }

    @Test
    fun formatCurrencyUsd_dollarPrefix() {
        assertEquals("$12.34", PayEstimate.formatCurrencyUsd(12.34))
        assertEquals("$0.00", PayEstimate.formatCurrencyUsd(0.0))
        assertEquals("$1000.50", PayEstimate.formatCurrencyUsd(1000.5))
    }
}
