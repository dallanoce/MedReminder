package io.github.dallanoce.medreminder.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyResetTest {

    private val resetTime = 1_000_000L

    @Test
    fun `clears a schedule started before the reset`() {
        assertTrue(AlarmScheduler.shouldClearOnReset(firstDoseTime = resetTime - 1, resetTime = resetTime))
    }

    @Test
    fun `keeps a schedule set after the reset time when the reset is delivered late`() {
        assertFalse(AlarmScheduler.shouldClearOnReset(firstDoseTime = resetTime + 60_000, resetTime = resetTime))
    }

    @Test
    fun `nothing to clear when no schedule is active`() {
        assertFalse(AlarmScheduler.shouldClearOnReset(firstDoseTime = null, resetTime = resetTime))
    }
}
