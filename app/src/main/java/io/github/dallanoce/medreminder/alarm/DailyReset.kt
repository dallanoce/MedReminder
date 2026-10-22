package io.github.dallanoce.medreminder.alarm

import java.util.Calendar
import java.util.TimeZone

/**
 * When a day's schedule ends. The schedule is cleared at the first 03:00 after it started, so the
 * user starts each day fresh; but never before its last dose, so a plan started in the evening
 * keeps its night doses (it is cleared right after the last one instead).
 */
object DailyReset {
    private const val RESET_HOUR = 3
    private const val AFTER_LAST_DOSE_MILLIS = 60_000L

    /** The first 03:00 strictly after [time]. */
    fun nextBoundary(time: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, RESET_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= time) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

    /** When the schedule that started at [firstDoseTime] and ends at [lastDoseTime] should be cleared. */
    fun clearTime(firstDoseTime: Long, lastDoseTime: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        maxOf(nextBoundary(firstDoseTime, timeZone), lastDoseTime + AFTER_LAST_DOSE_MILLIS)
}
