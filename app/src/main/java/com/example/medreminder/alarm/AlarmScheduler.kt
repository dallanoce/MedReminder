package com.example.medreminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val REMINDER_ID_OFFSET = 10000
    }

    fun scheduleAlarm(medId: Int, medName: String, timeInMillis: Long, isReminder: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            }
        }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
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
