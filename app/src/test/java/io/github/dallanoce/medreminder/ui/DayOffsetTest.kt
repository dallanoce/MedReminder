package io.github.dallanoce.medreminder.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DayOffsetTest {

    private val zone = TimeZone.getTimeZone("Europe/Rome")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun `same calendar day is 0`() {
        assertEquals(0, dayOffset(at(22, 0, 0), now = at(22, 23, 59), timeZone = zone))
    }

    @Test
    fun `after midnight is tomorrow`() {
        assertEquals(1, dayOffset(at(23, 0, 30), now = at(22, 23, 0), timeZone = zone))
    }

    @Test
    fun `before midnight is yesterday`() {
        assertEquals(-1, dayOffset(at(21, 22, 0), now = at(22, 1, 0), timeZone = zone))
    }

    @Test
    fun `daylight saving change keeps day boundaries`() {
        // Europe/Rome leaves summer time on 25 Oct 2026
        assertEquals(1, dayOffset(at(26, 0, 30), now = at(25, 23, 30), timeZone = zone))
        assertEquals(0, dayOffset(at(25, 23, 30), now = at(25, 0, 30), timeZone = zone))
    }
}
