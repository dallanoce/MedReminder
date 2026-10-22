package io.github.dallanoce.medreminder.ui

import java.util.Calendar
import java.util.TimeZone

/** How many calendar days [time] is from [now]: 0 today, 1 tomorrow, -1 yesterday. */
fun dayOffset(time: Long, now: Long, timeZone: TimeZone = TimeZone.getDefault()): Int {
    fun epochDay(millis: Long): Long {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        val localMillis = millis + calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)
        return Math.floorDiv(localMillis, 24 * 60 * 60 * 1000L)
    }
    return (epochDay(time) - epochDay(now)).toInt()
}
