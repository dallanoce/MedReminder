package io.github.dallanoce.medreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.dallanoce.medreminder.MainActivity
import io.github.dallanoce.medreminder.data.local.datastore.DataStoreManager
import io.github.dallanoce.medreminder.data.local.room.AppDatabase
import kotlinx.coroutines.flow.first

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val REMINDER_ID_OFFSET = 10000
        const val SNOOZE_ID_OFFSET = 20000
        const val DAILY_RESET_ID = 99999
        const val ACTION_DAILY_RESET = "io.github.dallanoce.medreminder.ACTION_DAILY_RESET"
        const val ACTION_DOSE_ALARM = "io.github.dallanoce.medreminder.ACTION_DOSE_ALARM"

        // Window for the daily reset when exact alarms are not allowed (no permission needed).
        private const val RESET_WINDOW_MILLIS = 15 * 60 * 1000L
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Schedules an exact alarm. Returns false, scheduling nothing, when the user hasn't allowed
     * exact alarms ("Alarms & reminders"); the permissions screen asks for it.
     */
    fun scheduleAlarm(medId: Int, medName: String, timeInMillis: Long, isReminder: Boolean = false): Boolean =
        scheduleExact(if (isReminder) medId + REMINDER_ID_OFFSET else medId, medId, medName, timeInMillis, isReminder)

    private fun scheduleExact(requestCode: Int, medId: Int, medName: String, timeInMillis: Long, isReminder: Boolean): Boolean {
        if (!canScheduleExactAlarms()) return false

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DOSE_ALARM
            putExtra(AlarmNotifications.EXTRA_MED_NAME, medName)
            putExtra(AlarmNotifications.EXTRA_MED_ID, medId)
            putExtra(AlarmNotifications.EXTRA_IS_REMINDER, isReminder)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (isReminder) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        } else {
            // Dose alarms (and snoozes) use the alarm-clock API: the system leaves Doze in time for
            // them, they aren't rate-limited like other while-idle alarms, and the next dose shows as
            // the next alarm in the status bar. Tapping it opens the app.
            val showApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(timeInMillis, showApp), pendingIntent)
        }
        return true
    }

    /**
     * The daily reset: clears the day's schedule once it is over (see [DailyReset]), then re-registers
     * everything. Runs when the reset alarm fires, after a reboot, when exact alarms are granted again
     * and on app start, so a reset missed while the phone was off still happens.
     */
    suspend fun runDailyReset() {
        val dataStoreManager = DataStoreManager(context)
        val firstDoseTime = dataStoreManager.firstDoseTime.first()
        if (firstDoseTime != null) {
            val medications = AppDatabase.getDatabase(context).medicationDao().getMedicationsOnce()
            val lastDoseTime = DoseSchedule.doses(firstDoseTime, medications).lastOrNull()?.time ?: firstDoseTime
            if (System.currentTimeMillis() >= DailyReset.clearTime(firstDoseTime, lastDoseTime)) {
                dataStoreManager.clearFirstDoseTime()
            }
        }
        rescheduleAll()
    }

    /**
     * Re-registers the remaining alarms of today's schedule from the saved plan, reading the current
     * plan, first dose time and reminder offset, and moves the daily reset to when this schedule ends.
     * Called whenever any of them changes. Snoozed alarms are left alone.
     */
    suspend fun rescheduleAll() {
        val dataStoreManager = DataStoreManager(context)
        val medications = AppDatabase.getDatabase(context).medicationDao().getMedicationsOnce()

        // Start clean so a dose that moved or no longer exists can't keep a stale alarm.
        medications.forEach { cancelDoseAlarms(it.id) }

        val now = System.currentTimeMillis()
        val firstDoseTime = dataStoreManager.firstDoseTime.first()
        if (firstDoseTime == null) {
            scheduleResetAt(DailyReset.nextBoundary(now))
            return
        }

        val doses = DoseSchedule.doses(firstDoseTime, medications)
        scheduleResetAt(DailyReset.clearTime(firstDoseTime, doses.lastOrNull()?.time ?: firstDoseTime))

        DoseSchedule.upcomingAlarms(
            firstDoseTime = firstDoseTime,
            medications = medications,
            reminderOffsetMinutes = dataStoreManager.reminderOffset.first(),
            now = now
        ).forEach { scheduleAlarm(it.medId, it.medName, it.time, it.isReminder) }
    }

    private fun scheduleResetAt(timeInMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_DAILY_RESET)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_RESET_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        } else {
            // Inexact window: needs no permission, so the app never crashes before it's granted.
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, timeInMillis, RESET_WINDOW_MILLIS, pendingIntent)
        }
    }

    fun snoozeAlarm(medId: Int, medName: String) {
        val snoozeTime = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes
        // Own request code, so rescheduling the day's doses doesn't drop a pending snooze.
        scheduleExact(medId + SNOOZE_ID_OFFSET, medId, medName, snoozeTime, isReminder = false)
    }

    /** Cancels everything for a medication: dose, reminder and snooze. */
    fun cancelAlarm(medId: Int) {
        cancelDoseAlarms(medId)
        cancelPendingIntent(medId + SNOOZE_ID_OFFSET)
    }

    private fun cancelDoseAlarms(medId: Int) {
        cancelPendingIntent(medId)
        cancelPendingIntent(medId + REMINDER_ID_OFFSET)
    }

    private fun cancelPendingIntent(requestCode: Int) {
        // Must match the scheduled intent (same action) to find it.
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_DOSE_ALARM)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
