package io.github.dallanoce.medreminder.permissions

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.NotificationManagerCompat
import io.github.dallanoce.medreminder.R

/**
 * Everything the dose alert needs to interrupt the user at the right time,
 * whether the phone is locked or in use.
 */
enum class AlertPermission(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    /** Without notifications nothing is shown while the phone is locked. */
    NOTIFICATIONS(R.string.perm_notifications_title, R.string.perm_notifications_desc),

    /** "Alarms & reminders": without it no dose alarm is scheduled (Android 12+). */
    EXACT_ALARMS(R.string.perm_exact_title, R.string.perm_exact_desc),

    /** Full-screen intent: shows the alarm over the lock screen (user-revocable on Android 14+). */
    FULL_SCREEN(R.string.perm_fullscreen_title, R.string.perm_fullscreen_desc),

    /** "Display over other apps": the only way to take over the screen while it's in use. */
    OVERLAY(R.string.perm_overlay_title, R.string.perm_overlay_desc);

    fun isGranted(context: Context): Boolean = when (this) {
        NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        EXACT_ALARMS -> Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        FULL_SCREEN -> Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        OVERLAY -> Settings.canDrawOverlays(context)
    }

    /** Whether this permission exists on the running Android version. */
    val isApplicable: Boolean
        get() = when (this) {
            NOTIFICATIONS, OVERLAY -> true
            EXACT_ALARMS -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            FULL_SCREEN -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        }

    /** The system settings page where the user can allow this permission for the app. */
    @SuppressLint("InlinedApi") // only requested where it exists, see isApplicable
    fun settingsIntent(context: Context): Intent {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        return when (this) {
            NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
            FULL_SCREEN -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
            OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
        }
    }

    companion object {
        fun missing(context: Context): List<AlertPermission> =
            entries.filter { it.isApplicable && !it.isGranted(context) }
    }
}
