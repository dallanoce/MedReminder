package io.github.dallanoce.medreminder.alarm

import io.github.dallanoce.medreminder.data.local.room.Medication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseScheduleTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val start = 1_800_000_000_000L // first dose time

    private fun med(id: Int, hours: Int = 0, minutes: Int = 0) =
        Medication(id = id, name = "Med$id", intervalHours = hours, intervalMinutes = minutes, orderIndex = id - 1)

    @Test
    fun `first medication is due at the first dose time and its own interval is ignored`() {
        val doses = DoseSchedule.doses(start, listOf(med(1, hours = 5)))
        assertEquals(start, doses.single().time)
    }

    @Test
    fun `each following dose is due its interval after the previous one`() {
        val doses = DoseSchedule.doses(start, listOf(med(1), med(2, hours = 2), med(3, minutes = 30), med(4, hours = 1, minutes = 15)))
        assertEquals(
            listOf(start, start + 2 * hour, start + 2 * hour + 30 * minute, start + 3 * hour + 45 * minute),
            doses.map { it.time }
        )
    }

    @Test
    fun `long intervals don't overflow`() {
        val doses = DoseSchedule.doses(start, listOf(med(1), med(2, hours = 700)))
        assertEquals(start + 700 * hour, doses[1].time)
    }

    @Test
    fun `alarms are planned only for doses and reminders still ahead`() {
        val meds = listOf(med(1), med(2, hours = 1), med(3, hours = 1))
        val now = start + 70 * minute // med 1 (start) and med 2 (+1h) are past; med 3 is at +2h
        val alarms = DoseSchedule.upcomingAlarms(start, meds, reminderOffsetMinutes = 15, now = now)
        assertEquals(
            listOf(
                PlannedAlarm(3, "Med3", start + 2 * hour - 15 * minute, isReminder = true),
                PlannedAlarm(3, "Med3", start + 2 * hour, isReminder = false)
            ),
            alarms
        )
    }

    @Test
    fun `reminder already past is skipped while the dose itself is still scheduled`() {
        val meds = listOf(med(1), med(2, minutes = 30))
        val now = start + 20 * minute // reminder for med 2 was at +15 min
        val alarms = DoseSchedule.upcomingAlarms(start, meds, reminderOffsetMinutes = 15, now = now)
        assertEquals(listOf(PlannedAlarm(2, "Med2", start + 30 * minute, isReminder = false)), alarms)
    }

    @Test
    fun `a first dose set in the future gets its own alarm and reminder`() {
        val alarms = DoseSchedule.upcomingAlarms(start, listOf(med(1)), reminderOffsetMinutes = 10, now = start - hour)
        assertEquals(
            listOf(
                PlannedAlarm(1, "Med1", start - 10 * minute, isReminder = true),
                PlannedAlarm(1, "Med1", start, isReminder = false)
            ),
            alarms
        )
    }

    @Test
    fun `a reminder offset of zero turns reminders off`() {
        val alarms = DoseSchedule.upcomingAlarms(start, listOf(med(1), med(2, hours = 1)), reminderOffsetMinutes = 0, now = start)
        assertTrue(alarms.none { it.isReminder })
        assertEquals(1, alarms.size)
    }

    @Test
    fun `nothing is planned when every dose is past`() {
        val alarms = DoseSchedule.upcomingAlarms(start, listOf(med(1), med(2, hours = 1)), reminderOffsetMinutes = 15, now = start + 2 * hour)
        assertTrue(alarms.isEmpty())
    }
}
