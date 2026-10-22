package io.github.dallanoce.medreminder.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.dallanoce.medreminder.R
import io.github.dallanoce.medreminder.data.local.datastore.DataStoreManager
import io.github.dallanoce.medreminder.ui.theme.MedReminderTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmActivity : AppCompatActivity() {
    /** One alarm waiting for the user on this screen. */
    private data class PendingAlarm(
        val medId: Int,
        val medName: String,
        val isReminder: Boolean,
        val reminderOffset: Int
    )

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var ringingHere = false
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var dataStoreManager: DataStoreManager

    // Alarms can arrive while this screen is open (e.g. two medications due at the same time):
    // they queue up and are handled one at a time, newest first.
    private val pending = mutableStateListOf<PendingAlarm>()

    // Takes an alarm off this screen when it's handled from the notification's buttons.
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            remove(
                intent.getIntExtra(AlarmNotifications.EXTRA_MED_ID, -1),
                intent.getBooleanExtra(AlarmNotifications.EXTRA_IS_REMINDER, false)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alarmScheduler = AlarmScheduler(this)
        dataStoreManager = DataStoreManager(this)

        setupWindow()

        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            IntentFilter(AlarmActionReceiver.ACTION_CLOSE_ALARM_SCREEN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        add(intent)
        if (pending.isEmpty()) {
            finish()
            return
        }

        setContent {
            val userName by dataStoreManager.userName.collectAsState(initial = null)
            MedReminderTheme {
                pending.lastOrNull()?.let { alarm ->
                    AlarmContent(alarm, waiting = pending.size - 1, userName = userName)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        add(intent)
    }

    private fun add(intent: Intent) {
        val medName = intent.getStringExtra(AlarmNotifications.EXTRA_MED_NAME) ?: return
        val alarm = PendingAlarm(
            medId = intent.getIntExtra(AlarmNotifications.EXTRA_MED_ID, -1),
            medName = medName,
            isReminder = intent.getBooleanExtra(AlarmNotifications.EXTRA_IS_REMINDER, false),
            reminderOffset = intent.getIntExtra(AlarmNotifications.EXTRA_REMINDER_OFFSET, 15)
        )
        pending.removeAll { it.medId == alarm.medId && it.isReminder == alarm.isReminder }
        pending.add(alarm)

        // The notification normally plays the alarm sound. Only if it can't (the user turned off the
        // app's notifications or the channel's sound) does this screen ring by itself.
        if (!ringingHere && !AlarmNotifications.isSounding(this, alarm.medId, alarm.isReminder)) {
            ringingHere = true
            lifecycleScope.launch {
                startAlarm(dataStoreManager.ringtoneUri.first())
            }
        }
    }

    /** Takes an alarm off the screen, and closes the screen when none is left. */
    private fun remove(medId: Int, isReminder: Boolean) {
        pending.removeAll { it.medId == medId && it.isReminder == isReminder }
        if (pending.isEmpty()) {
            stopAlarm()
            finish()
        }
    }

    private fun onTaken(alarm: PendingAlarm) {
        AlarmNotifications.cancel(this, alarm.medId, alarm.isReminder)
        remove(alarm.medId, alarm.isReminder)
    }

    private fun onSnooze(alarm: PendingAlarm) {
        AlarmNotifications.cancel(this, alarm.medId, isReminder = false)
        alarmScheduler.snoozeAlarm(alarm.medId, alarm.medName)
        remove(alarm.medId, isReminder = false)
    }

    @Composable
    private fun AlarmContent(alarm: PendingAlarm, waiting: Int, userName: String?) {
        val backgroundColor = if (alarm.isReminder) {
            ComposeColor(0xFFE8F5E9) // Light Green
        } else {
            MaterialTheme.colorScheme.errorContainer
        }

        val contentColor = if (alarm.isReminder) {
            ComposeColor(0xFF2E7D32) // Dark Green
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor
        ) {
            // Centred, and scrollable when large system fonts or a long name don't fit
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (alarm.isReminder) stringResource(R.string.reminder_title) else stringResource(R.string.alarm_title),
                    fontSize = 20.sp,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(24.dp))

                val message = when {
                    alarm.isReminder -> stringResource(R.string.reminder_message, alarm.reminderOffset, alarm.medName)
                    !userName.isNullOrEmpty() -> stringResource(R.string.alarm_message, userName, alarm.medName)
                    else -> stringResource(R.string.alarm_message_default, alarm.medName)
                }

                Text(
                    text = message,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 36.sp,
                    color = contentColor
                )

                if (waiting > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = pluralStringResource(R.plurals.alarm_more_waiting, waiting, waiting),
                        fontSize = 18.sp,
                        color = contentColor
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                if (alarm.isReminder) {
                    Button(
                        onClick = { onTaken(alarm) },
                        modifier = Modifier.fillMaxWidth(0.8f),
                        colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF2E7D32))
                    ) {
                        Text(stringResource(R.string.alarm_ok_btn), fontSize = 24.sp)
                    }
                } else {
                    Button(
                        onClick = { onTaken(alarm) },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text(stringResource(R.string.alarm_taken_btn), fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { onSnooze(alarm) },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        Text(stringResource(R.string.alarm_snooze_btn), fontSize = 18.sp)
                    }
                }
            }
        }
    }

    // Shows over the lock screen without unlocking it: asking to dismiss the keyguard would put the
    // PIN prompt on top of the alarm.
    private fun setupWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun startAlarm(savedRingtoneUri: String?) {
        ringtone = playFirstAvailableSound(savedRingtoneUri)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    /**
     * Plays the chosen ringtone, falling back to the default alarm and then the default notification
     * sound when it can't play (e.g. a ringtone that was deleted, or restored from another phone).
     * The sound loops until the user reacts.
     */
    private fun playFirstAvailableSound(savedRingtoneUri: String?): Ringtone? {
        val candidates = listOfNotNull(
            savedRingtoneUri?.let { Uri.parse(it) },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (uri in candidates) {
            val candidate = RingtoneManager.getRingtone(applicationContext, uri) ?: continue
            candidate.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                candidate.isLooping = true
            }
            candidate.play()
            if (candidate.isPlaying) return candidate
            candidate.stop()
        }
        return null
    }

    private fun stopAlarm() {
        ringtone?.stop()
        vibrator?.cancel()
        ringingHere = false
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(closeReceiver)
        stopAlarm()
    }
}
