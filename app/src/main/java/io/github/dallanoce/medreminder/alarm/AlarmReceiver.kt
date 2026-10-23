package io.github.dallanoce.medreminder.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import io.github.dallanoce.medreminder.data.local.datastore.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        // The system holds new notifications for a fraction of a second before showing them.
        private const val FULL_SCREEN_INTENT_WAIT_MILLIS = 1000L
    }

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

                // While the phone is in use, the system only shows the notification as a heads-up and
                // blocks activity starts from the background, unless the user allowed
                // "Display over other apps": then the alarm screen takes over in every state.
                val takesOver = Settings.canDrawOverlays(context)
                val screenOn = context.getSystemService(PowerManager::class.java).isInteractive

                // Always post the notification: it rings until the user reacts, opens the alarm screen
                // over the lock screen, and offers Taken / Snooze. With the screen on, it only shows as
                // a heads-up whatever the app does: when the alarm screen takes over anyway, it goes
                // without a full-screen intent, which would keep that heads-up on top of it.
                AlarmNotifications.show(
                    context.withAppLanguage(dataStoreManager.language.first()),
                    medName, medId, dataStoreManager.userName.first(), isReminder,
                    reminderOffset, dataStoreManager.ringtoneUri.first(),
                    fullScreen = !(takesOver && screenOn)
                )

                if (takesOver) {
                    if (!screenOn) {
                        // Let the system open the alarm screen from the notification first: it shows
                        // notifications a moment after they're posted, and if the alarm screen had
                        // already turned the screen on, it would take the phone for in use and show
                        // the notification as a heads-up on top of it. Opening it from here is then
                        // only a backup (e.g. "Full screen notifications" not allowed).
                        delay(FULL_SCREEN_INTENT_WAIT_MILLIS)
                        if (AlarmNotifications.wasHandled(medId, isReminder)) return@launch
                    }
                    context.startActivity(
                        AlarmNotifications.alarmActivityIntent(context, medName, medId, isReminder, reminderOffset)
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * Below Android 13 the in-app language only applies inside activities: apply it to the
     * notification texts too. From Android 13 the system applies it to the whole app.
     */
    private fun Context.withAppLanguage(languageCode: String?): Context {
        if (languageCode.isNullOrEmpty() || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return this
        val configuration = Configuration(resources.configuration).apply {
            setLocale(Locale.forLanguageTag(languageCode))
        }
        return createConfigurationContext(configuration)
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
