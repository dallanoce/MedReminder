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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
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

class AlarmActivity : ComponentActivity() {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var dataStoreManager: DataStoreManager
    private var medId = -1

    // Closes this screen when the alarm is handled from the notification's buttons.
    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getIntExtra(AlarmNotifications.EXTRA_MED_ID, -1) == medId) {
                stopAlarm()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alarmScheduler = AlarmScheduler(this)
        dataStoreManager = DataStoreManager(this)

        setupWindow()

        val medName = intent.getStringExtra(AlarmNotifications.EXTRA_MED_NAME) ?: "Medication"
        medId = intent.getIntExtra(AlarmNotifications.EXTRA_MED_ID, -1)
        val isReminder = intent.getBooleanExtra(AlarmNotifications.EXTRA_IS_REMINDER, false)
        val reminderOffset = intent.getIntExtra(AlarmNotifications.EXTRA_REMINDER_OFFSET, 15)

        ContextCompat.registerReceiver(
            this,
            closeReceiver,
            IntentFilter(AlarmActionReceiver.ACTION_CLOSE_ALARM_SCREEN),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // The notification normally plays the alarm sound. Only if it can't (the user turned off the
        // app's notifications or the channel's sound) does this screen ring by itself.
        if (!AlarmNotifications.isSounding(this, medId, isReminder)) {
            lifecycleScope.launch {
                val savedRingtoneUri = dataStoreManager.ringtoneUri.first()
                startAlarm(savedRingtoneUri)
            }
        }

        setContent {
            val userName by dataStoreManager.userName.collectAsState(initial = null)

            MedReminderTheme {
                val backgroundColor = if (isReminder) {
                    ComposeColor(0xFFE8F5E9) // Light Green
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }

                val contentColor = if (isReminder) {
                    ComposeColor(0xFF2E7D32) // Dark Green
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = backgroundColor
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isReminder) stringResource(R.string.reminder_title) else stringResource(R.string.alarm_title),
                            fontSize = 20.sp,
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        val message = if (isReminder) {
                            stringResource(R.string.reminder_message, reminderOffset, medName)
                        } else {
                            if (!userName.isNullOrEmpty()) {
                                stringResource(R.string.alarm_message, userName!!, medName)
                            } else {
                                stringResource(R.string.alarm_message_default, medName)
                            }
                        }

                        Text(
                            text = message,
                            fontSize = 28.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 36.sp,
                            color = contentColor
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        if (isReminder) {
                            Button(
                                onClick = {
                                    stopAlarm()
                                    AlarmNotifications.cancel(this@AlarmActivity, medId, isReminder = true)
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth(0.8f),
                                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF2E7D32))
                            ) {
                                Text(stringResource(R.string.alarm_ok_btn), fontSize = 24.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    stopAlarm()
                                    AlarmNotifications.cancel(this@AlarmActivity, medId, isReminder = false)
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Text(stringResource(R.string.alarm_taken_btn), fontSize = 24.sp)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = {
                                    snoozeAlarm(medName, medId)
                                    finish()
                                },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Text(stringResource(R.string.alarm_snooze_btn), fontSize = 18.sp)
                            }
                        }
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
    }

    private fun snoozeAlarm(medName: String, medId: Int) {
        stopAlarm()
        AlarmNotifications.cancel(this, medId, isReminder = false)
        alarmScheduler.snoozeAlarm(medId, medName)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(closeReceiver)
        stopAlarm()
    }
}
