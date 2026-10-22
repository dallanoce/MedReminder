package io.github.dallanoce.medreminder.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        alarmScheduler = AlarmScheduler(this)
        dataStoreManager = DataStoreManager(this)
        
        setupWindow()
        
        val medName = intent.getStringExtra("MED_NAME") ?: "Medication"
        val medId = intent.getIntExtra("MED_ID", -1)
        val isReminder = intent.getBooleanExtra("IS_REMINDER", false)
        val reminderOffset = intent.getIntExtra("REMINDER_OFFSET", 15)

        lifecycleScope.launch {
            val savedRingtoneUri = dataStoreManager.ringtoneUri.first()
            startAlarm(savedRingtoneUri)
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
                                    dismissNotification(medId + AlarmScheduler.REMINDER_ID_OFFSET)
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
                                    dismissNotification(medId)
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

    private fun setupWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
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

    private fun dismissNotification(id: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(id)
    }

    private fun snoozeAlarm(medName: String, medId: Int) {
        stopAlarm()
        dismissNotification(medId)
        alarmScheduler.snoozeAlarm(medId, medName)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}
