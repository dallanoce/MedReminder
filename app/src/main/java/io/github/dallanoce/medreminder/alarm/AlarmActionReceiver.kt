package io.github.dallanoce.medreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the Taken / Snooze / OK buttons of the dose notification. Not exported. */
class AlarmActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_TAKEN = "io.github.dallanoce.medreminder.action.TAKEN"
        const val ACTION_SNOOZE = "io.github.dallanoce.medreminder.action.SNOOZE"
        const val ACTION_DISMISS = "io.github.dallanoce.medreminder.action.DISMISS"

        /** Tells an open alarm screen that the alarm was handled from the notification. */
        const val ACTION_CLOSE_ALARM_SCREEN = "io.github.dallanoce.medreminder.action.CLOSE_ALARM_SCREEN"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medId = intent.getIntExtra(AlarmNotifications.EXTRA_MED_ID, -1)
        val medName = intent.getStringExtra(AlarmNotifications.EXTRA_MED_NAME) ?: return
        val isReminder = intent.getBooleanExtra(AlarmNotifications.EXTRA_IS_REMINDER, false)

        when (intent.action) {
            ACTION_TAKEN, ACTION_DISMISS -> Unit
            ACTION_SNOOZE -> AlarmScheduler(context).snoozeAlarm(medId, medName)
            else -> return
        }
        AlarmNotifications.cancel(context, medId, isReminder)
        context.sendBroadcast(
            Intent(ACTION_CLOSE_ALARM_SCREEN)
                .setPackage(context.packageName)
                .putExtra(AlarmNotifications.EXTRA_MED_ID, medId)
        )
    }
}
