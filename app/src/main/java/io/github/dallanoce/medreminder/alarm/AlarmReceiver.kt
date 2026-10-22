package io.github.dallanoce.medreminder.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import io.github.dallanoce.medreminder.data.local.datastore.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    // Not exported: only the system (boot, permission change) and this app's own alarms reach it.
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Alarms don't survive a reboot, and revoking exact alarms deletes them: restore them
            // (clearing the day first if it ended meanwhile). The daily reset does the same.
            Intent.ACTION_BOOT_COMPLETED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            AlarmScheduler.ACTION_DAILY_RESET -> runDailyReset(context)

            AlarmScheduler.ACTION_DOSE_ALARM -> {
                val medName = intent.getStringExtra(AlarmNotifications.EXTRA_MED_NAME) ?: return
                val medId = intent.getIntExtra(AlarmNotifications.EXTRA_MED_ID, -1)
                val isReminder = intent.getBooleanExtra(AlarmNotifications.EXTRA_IS_REMINDER, false)
                deliverAlarm(context, medName, medId, isReminder)
            }
        }
    }

    private fun deliverAlarm(context: Context, medName: String, medId: Int, isReminder: Boolean) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataStoreManager = DataStoreManager(context)
                val reminderOffset = dataStoreManager.reminderOffset.first()

                // Always post the notification: it rings until the user reacts, opens the alarm screen
                // over the lock screen, and offers Taken / Snooze.
                AlarmNotifications.show(
                    context, medName, medId, dataStoreManager.userName.first(), isReminder,
                    reminderOffset, dataStoreManager.ringtoneUri.first()
                )

                // While the phone is in use, the system only shows the notification as a heads-up and
                // blocks activity starts from the background, unless the user allowed
                // "Display over other apps": then the alarm screen takes over in every state.
                if (Settings.canDrawOverlays(context)) {
                    context.startActivity(
                        AlarmNotifications.alarmActivityIntent(context, medName, medId, isReminder, reminderOffset)
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun runDailyReset(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmScheduler(context).runDailyReset()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
