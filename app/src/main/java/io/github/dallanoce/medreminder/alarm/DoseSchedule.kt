package io.github.dallanoce.medreminder.alarm

import io.github.dallanoce.medreminder.data.local.room.Medication

/** One dose of today's schedule. */
data class ScheduledDose(val medication: Medication, val time: Long)

/** An alarm to register with AlarmManager: the dose alarm itself, or the early reminder before it. */
data class PlannedAlarm(val medId: Int, val medName: String, val time: Long, val isReminder: Boolean)

/**
 * The single source of truth for today's schedule, used for scheduling alarms and for the Home screen.
 *
 * The first medication is due at the first dose time; every following one is due its interval
 * after the previous one (the first medication's own interval is ignored).
 */
object DoseSchedule {
    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS

    fun intervalMillis(medication: Medication): Long =
        medication.intervalHours * HOUR_MILLIS + medication.intervalMinutes * MINUTE_MILLIS

    fun doses(firstDoseTime: Long, medications: List<Medication>): List<ScheduledDose> {
        var time = firstDoseTime
        return medications.mapIndexed { index, medication ->
            if (index > 0) time += intervalMillis(medication)
            ScheduledDose(medication, time)
        }
    }

    /**
     * The alarms still ahead of [now]. Doses (and reminders) already in the past are skipped, so setting
     * a first dose time earlier in the day doesn't make every overdue alarm ring at once.
     * A [reminderOffsetMinutes] of 0 turns the early reminder off.
     */
    fun upcomingAlarms(
        firstDoseTime: Long,
        medications: List<Medication>,
        reminderOffsetMinutes: Int,
        now: Long
    ): List<PlannedAlarm> = doses(firstDoseTime, medications).flatMap { dose ->
        buildList {
            val med = dose.medication
            if (reminderOffsetMinutes > 0) {
                val reminderTime = dose.time - reminderOffsetMinutes * MINUTE_MILLIS
                if (reminderTime > now) add(PlannedAlarm(med.id, med.name, reminderTime, isReminder = true))
            }
            if (dose.time > now) add(PlannedAlarm(med.id, med.name, dose.time, isReminder = false))
        }
    }
}
