package com.rmltd.workhourstracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypedHoursParseTest {

    @Test
    fun acceptsZeroAndDecimals() {
        assertEquals(0.0, TypedHoursParse.parse("0")!!, 0.0)
        assertEquals(0.0, TypedHoursParse.parse("0.0")!!, 0.0)
        assertEquals(7.5, TypedHoursParse.parse("7.5")!!, 0.0)
        assertEquals(8.0, TypedHoursParse.parse("8")!!, 0.0)
        assertEquals(24.0, TypedHoursParse.parse("24")!!, 0.0)
    }

    @Test
    fun rejectsColonAndJunk() {
        assertNull(TypedHoursParse.parse("7:30"))
        assertNull(TypedHoursParse.parse("7:30 AM"))
        assertNull(TypedHoursParse.parse(""))
        assertNull(TypedHoursParse.parse("abc"))
        assertNull(TypedHoursParse.parse("-1"))
        assertNull(TypedHoursParse.parse("25"))
        assertNull(TypedHoursParse.parse("7,5"))
        assertFalse(TypedHoursParse.isValid("7:30"))
        assertTrue(TypedHoursParse.isValid("7.5"))
    }
}
