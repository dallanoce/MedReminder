package io.github.dallanoce.medreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.dallanoce.medreminder.data.local.datastore.DataStoreManager
import io.github.dallanoce.medreminder.data.local.room.AppDatabase
import kotlinx.coroutines.flow.first
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val REMINDER_ID_OFFSET = 10000
        const val DAILY_RESET_ID = 99999
        const val ACTION_DAILY_RESET = "io.github.dallanoce.medreminder.ACTION_DAILY_RESET"
        const val EXTRA_RESET_TIME = "RESET_TIME"

        // Window for the daily reset when exact alarms are not allowed (no permission needed).
        private const val RESET_WINDOW_MILLIS = 15 * 60 * 1000L

        /**
         * The daily reset only clears a schedule that was started before the reset time,
         * so a reset delivered late can't wipe a schedule the user already set for the new day.
         */
        fun shouldClearOnReset(firstDoseTime: Long?, resetTime: Long): Boolean =
            firstDoseTime != null && firstDoseTime < resetTime
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Schedules an exact alarm. Returns false, scheduling nothing, when the user hasn't allowed
     * exact alarms ("Alarms & reminders"); the permissions screen asks for it.
     */
    fun scheduleAlarm(medId: Int, medName: String, timeInMillis: Long, isReminder: Boolean = false): Boolean {
        if (!canScheduleExactAlarms()) return false

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("MED_NAME", medName)
            putExtra("MED_ID", medId)
            putExtra("IS_REMINDER", isReminder)
        }

        val requestCode = if (isReminder) medId + REMINDER_ID_OFFSET else medId

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            timeInMillis,
            pendingIntent
        )
        return true
    }

    fun scheduleDailyReset() {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If it's already past 3 AM today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DAILY_RESET
            putExtra(EXTRA_RESET_TIME, calendar.timeInMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_RESET_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            // Inexact window: needs no permission, so the app never crashes before it's granted.
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                RESET_WINDOW_MILLIS,
                pendingIntent
            )
        }
    }

    /**
     * Re-registers the remaining alarms of today's schedule from the saved plan.
     * Called after a reboot, when exact alarms are granted again (revoking them deletes every
     * pending alarm) and on app start. Re-registering an alarm that already exists is harmless.
     */
    suspend fun rescheduleAll() {
        val dataStoreManager = DataStoreManager(context)
        val firstDoseTime = dataStoreManager.firstDoseTime.first() ?: return
        val reminderOffset = dataStoreManager.reminderOffset.first()
        val medications = AppDatabase.getDatabase(context).medicationDao().getAllMedications().first()

        var currentTime = firstDoseTime
        medications.forEach { med ->
            if (med != medications.firstOrNull()) {
                currentTime += (med.intervalHours * 3600 + med.intervalMinutes * 60) * 1000
            }
            if (currentTime > System.currentTimeMillis()) {
                scheduleAlarm(med.id, med.name, currentTime)
            }
            val reminderTime = currentTime - (reminderOffset * 60 * 1000)
            if (reminderTime > System.currentTimeMillis()) {
                scheduleAlarm(med.id, med.name, reminderTime, isReminder = true)
            }
        }
    }

    fun snoozeAlarm(medId: Int, medName: String) {
        val snoozeTime = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes
        scheduleAlarm(medId, medName, snoozeTime, isReminder = false)
    }

    fun cancelAlarm(medId: Int) {
        // Cancel main alarm
        cancelPendingIntent(medId)
        // Cancel reminder
        cancelPendingIntent(medId + REMINDER_ID_OFFSET)
    }

    private fun cancelPendingIntent(requestCode: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}
