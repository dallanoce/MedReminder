package io.github.dallanoce.medreminder.alarm

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailyResetTest {

    private val zone = TimeZone.getTimeZone("Europe/Rome")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun `next boundary is the coming 03_00`() {
        assertEquals(at(23, 3), DailyReset.nextBoundary(at(22, 17), zone))
        assertEquals(at(22, 3), DailyReset.nextBoundary(at(22, 1), zone))
    }

    @Test
    fun `next boundary at exactly 03_00 is the following day`() {
        assertEquals(at(23, 3), DailyReset.nextBoundary(at(22, 3), zone))
    }

    @Test
    fun `a daytime schedule is cleared at the next 03_00`() {
        assertEquals(at(23, 3), DailyReset.clearTime(firstDoseTime = at(22, 8), lastDoseTime = at(22, 20), timeZone = zone))
    }

    @Test
    fun `an evening schedule keeps its night doses and is cleared right after the last one`() {
        assertEquals(at(23, 4, 1), DailyReset.clearTime(firstDoseTime = at(22, 20), lastDoseTime = at(23, 4), timeZone = zone))
    }

    @Test
    fun `a schedule started after midnight runs until its last dose`() {
        assertEquals(at(22, 10, 1), DailyReset.clearTime(firstDoseTime = at(22, 1), lastDoseTime = at(22, 10), timeZone = zone))
    }

    @Test
    fun `a single dose just before 03_00 is cleared at 03_00`() {
        assertEquals(at(22, 3), DailyReset.clearTime(firstDoseTime = at(22, 2, 59), lastDoseTime = at(22, 2, 59), timeZone = zone))
    }

    @Test
    fun `daylight saving change does not shift the 03_00 boundary`() {
        // Europe/Rome leaves summer time on 25 Oct 2026 at 03:00
        assertEquals(at(26, 3), DailyReset.nextBoundary(at(25, 12), zone))
    }
}
