package io.github.dallanoce.medreminder.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.core.app.NotificationManagerCompat
import io.github.dallanoce.medreminder.R
import java.util.concurrent.ConcurrentHashMap

/**
 * The dose notification is what makes the alarm audible: it rings on the alarm stream and repeats
 * until the user reacts, whether or not the alarm screen manages to open. It also opens the alarm
 * screen over the lock screen (full-screen intent) and offers Taken / Snooze without opening the app.
 */
object AlarmNotifications {
    private const val DOSE_CHANNEL_PREFIX = "dose_alarm_"
    private const val REMINDER_CHANNEL_ID = "dose_reminder"
    private val LEGACY_CHANNEL_IDS = listOf("med_alarm_channel_v3")
    private val VIBRATION_PATTERN = longArrayOf(0, 500, 500)

    const val EXTRA_MED_NAME = "MED_NAME"
    const val EXTRA_MED_ID = "MED_ID"
    const val EXTRA_IS_REMINDER = "IS_REMINDER"
    const val EXTRA_REMINDER_OFFSET = "REMINDER_OFFSET"

    // Alarms handled (Taken / Snooze / OK) since their notification was last posted.
    private val handled = ConcurrentHashMap.newKeySet<Int>()

    fun notificationId(medId: Int, isReminder: Boolean): Int =
        if (isReminder) medId + AlarmScheduler.REMINDER_ID_OFFSET else medId

    /**
     * [fullScreen]: whether the notification opens the alarm screen by itself (full-screen intent).
     * While the screen is on the system only shows it as a heads-up, and a full-screen intent keeps
     * that heads-up on top of everything until swiped away: leave it out when the app opens the alarm
     * screen itself, so the heads-up times out instead of covering the alarm screen.
     */
    fun show(
        context: Context,
        medName: String,
        medId: Int,
        userName: String?,
        isReminder: Boolean,
        reminderOffset: Int,
        ringtoneUri: String?,
        fullScreen: Boolean
    ) {
        val channelId = if (isReminder) ensureReminderChannel(context) else ensureDoseChannel(context, ringtoneUri)
        val id = notificationId(medId, isReminder)
        handled.remove(id)

        val alarmScreen = PendingIntent.getActivity(
            context,
            id,
            alarmActivityIntent(context, medName, medId, isReminder, reminderOffset),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(if (isReminder) R.string.reminder_title else R.string.alarm_title)
        val text = when {
            isReminder -> context.getString(R.string.reminder_message, reminderOffset, medName)
            !userName.isNullOrEmpty() -> context.getString(R.string.alarm_message, userName, medName)
            else -> context.getString(R.string.alarm_message_default, medName)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(alarmScreen)
            .setAutoCancel(false)
            .setOngoing(true)

        if (fullScreen) {
            builder.setFullScreenIntent(alarmScreen, true)
        }

        if (isReminder) {
            builder.addAction(0, context.getString(R.string.alarm_ok_btn),
                actionIntent(context, AlarmActionReceiver.ACTION_DISMISS, medId, medName, isReminder = true))
        } else {
            builder.addAction(0, context.getString(R.string.alarm_taken_btn),
                actionIntent(context, AlarmActionReceiver.ACTION_TAKEN, medId, medName, isReminder = false))
            builder.addAction(0, context.getString(R.string.alarm_snooze_btn),
                actionIntent(context, AlarmActionReceiver.ACTION_SNOOZE, medId, medName, isReminder = false))
        }

        val notification = builder.build()
        if (!isReminder) {
            // Keep ringing until the user reacts, like an alarm clock.
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Notifications not allowed: nothing can be posted. The alarm screen can still take over
            // via "Display over other apps", and the permissions screen warns the user.
        }
    }

    /** Called whenever an alarm is handled, from the alarm screen or from the notification. */
    fun cancel(context: Context, medId: Int, isReminder: Boolean) {
        handled.add(notificationId(medId, isReminder))
        NotificationManagerCompat.from(context).cancel(notificationId(medId, isReminder))
    }

    /** Whether the alarm was handled since its notification was posted. */
    fun wasHandled(medId: Int, isReminder: Boolean): Boolean = notificationId(medId, isReminder) in handled

    /**
     * Whether the posted notification is sounding the alarm. When it isn't (the user turned off the
     * app's notifications or the channel's sound), the alarm screen has to play the sound itself.
     */
    fun isSounding(context: Context, medId: Int, isReminder: Boolean): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        val posted = manager.activeNotifications.firstOrNull { it.id == notificationId(medId, isReminder) }
            ?: return false
        val channel = manager.getNotificationChannel(posted.notification.channelId) ?: return false
        return channel.importance >= NotificationManager.IMPORTANCE_DEFAULT && channel.sound != null
    }

    fun alarmActivityIntent(context: Context, medName: String, medId: Int, isReminder: Boolean, reminderOffset: Int) =
        Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_MED_NAME, medName)
            putExtra(EXTRA_MED_ID, medId)
            putExtra(EXTRA_IS_REMINDER, isReminder)
            putExtra(EXTRA_REMINDER_OFFSET, reminderOffset)
        }

    private fun actionIntent(context: Context, action: String, medId: Int, medName: String, isReminder: Boolean): PendingIntent {
        val intent = Intent(context, AlarmActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_MED_ID, medId)
            putExtra(EXTRA_MED_NAME, medName)
            putExtra(EXTRA_IS_REMINDER, isReminder)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId(medId, isReminder),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * A channel's sound can't change once created, so the dose channel's id includes the chosen sound:
     * picking another ringtone creates a new channel and removes the old ones.
     */
    private fun ensureDoseChannel(context: Context, ringtoneUri: String?): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val sound = resolveAlarmSound(context, ringtoneUri)
        val channelId = DOSE_CHANNEL_PREFIX + Integer.toHexString(sound.toString().hashCode())

        manager.notificationChannels
            .filter { (it.id.startsWith(DOSE_CHANNEL_PREFIX) && it.id != channelId) || it.id in LEGACY_CHANNEL_IDS }
            .forEach { manager.deleteNotificationChannel(it.id) }

        if (manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    context.getString(R.string.channel_dose_alarms),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_dose_alarms_desc)
                    setSound(
                        sound,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
        return channelId
    }

    private fun ensureReminderChannel(context: Context): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(REMINDER_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    context.getString(R.string.channel_reminders),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_reminders_desc)
                    enableVibration(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
        return REMINDER_CHANNEL_ID
    }

    /** The chosen ringtone if it can still be read, else the default alarm sound, else the default notification sound. */
    private fun resolveAlarmSound(context: Context, ringtoneUri: String?): Uri =
        listOfNotNull(
            ringtoneUri?.toUri(),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ).firstOrNull { canOpen(context, it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun canOpen(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (e: Exception) {
        false
    }
}
