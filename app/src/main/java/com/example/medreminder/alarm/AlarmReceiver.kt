package com.example.medreminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.medreminder.R
import com.example.medreminder.data.local.datastore.DataStoreManager
import com.example.medreminder.data.local.room.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            rescheduleAlarms(context)
            return
        }

        val medName = intent.getStringExtra("MED_NAME") ?: "Medication"
        val medId = intent.getIntExtra("MED_ID", -1)
        val isReminder = intent.getBooleanExtra("IS_REMINDER", false)

        val dataStoreManager = DataStoreManager(context)
        val userName = runBlocking { dataStoreManager.userName.first() }
        val reminderOffset = runBlocking { dataStoreManager.reminderOffset.first() }

        showNotification(context, medName, medId, userName, isReminder, reminderOffset)
        
        // Try to start activity directly as well
        startAlarmActivity(context, medName, medId, isReminder, reminderOffset)
    }

    private fun startAlarmActivity(context: Context, medName: String, medId: Int, isReminder: Boolean, reminderOffset: Int) {
        val activityIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                   Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                   Intent.FLAG_ACTIVITY_SINGLE_TOP or
                   Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("MED_NAME", medName)
            putExtra("MED_ID", medId)
            putExtra("IS_REMINDER", isReminder)
            putExtra("REMINDER_OFFSET", reminderOffset)
        }
        
        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            // Background start might be blocked, notification's fullScreenIntent will handle it
        }
    }

    private fun rescheduleAlarms(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataStoreManager = DataStoreManager(context)
                val firstDoseTime = dataStoreManager.firstDoseTime.first()
                val reminderOffset = dataStoreManager.reminderOffset.first()
                
                if (firstDoseTime != null) {
                    val database = AppDatabase.getDatabase(context)
                    val medications = database.medicationDao().getAllMedications().first()
                    val alarmScheduler = AlarmScheduler(context)
                    
                    var currentTime = firstDoseTime
                    medications.forEach { med ->
                        if (med == medications.firstOrNull()) {
                            scheduleAlarmAndReminder(alarmScheduler, med.id, med.name, currentTime, reminderOffset)
                        } else {
                            currentTime += (med.intervalHours * 3600 + med.intervalMinutes * 60) * 1000
                            scheduleAlarmAndReminder(alarmScheduler, med.id, med.name, currentTime, reminderOffset)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun scheduleAlarmAndReminder(scheduler: AlarmScheduler, id: Int, name: String, time: Long, offsetMin: Int) {
        if (time > System.currentTimeMillis()) {
            scheduler.scheduleAlarm(id, name, time)
        }
        val reminderTime = time - (offsetMin * 60 * 1000)
        if (reminderTime > System.currentTimeMillis()) {
            scheduler.scheduleAlarm(id, name, reminderTime, isReminder = true)
        }
    }

    private fun showNotification(context: Context, medName: String, medId: Int, userName: String?, isReminder: Boolean, reminderOffset: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "med_alarm_channel_v2" // Changed ID to force recreation with new settings

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Medication Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority alarms for medication"
                setBypassDnd(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 500, 500)
                setSound(alarmSound, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                   Intent.FLAG_ACTIVITY_NO_USER_ACTION or 
                   Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("MED_NAME", medName)
            putExtra("MED_ID", medId)
            putExtra("IS_REMINDER", isReminder)
            putExtra("REMINDER_OFFSET", reminderOffset)
        }
        
        val notificationId = if (isReminder) medId + AlarmScheduler.REMINDER_ID_OFFSET else medId

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isReminder) {
            context.getString(R.string.reminder_title)
        } else {
            context.getString(R.string.alarm_title)
        }

        val contentText = if (isReminder) {
            context.getString(R.string.reminder_message, reminderOffset, medName)
        } else {
            if (!userName.isNullOrEmpty()) {
                context.getString(R.string.alarm_message, userName, medName)
            } else {
                context.getString(R.string.alarm_message_default, medName)
            }
        }

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setSound(alarmSound)
            .setVibrate(longArrayOf(0, 500, 500, 500))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
