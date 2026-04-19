package com.example.medreminder.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.medreminder.R
import com.example.medreminder.data.local.datastore.DataStoreManager
import com.example.medreminder.ui.theme.MedReminderTheme
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
        
        // Turn screen on and show over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        
        val medName = intent.getStringExtra("MED_NAME") ?: "Medication"
        val medId = intent.getIntExtra("MED_ID", -1)

        lifecycleScope.launch {
            val savedRingtoneUri = dataStoreManager.ringtoneUri.first()
            startAlarm(savedRingtoneUri)
        }

        setContent {
            val userName by dataStoreManager.userName.collectAsState(initial = null)
            
            MedReminderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = stringResource(R.string.alarm_title),
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        val message = if (!userName.isNullOrEmpty()) {
                            stringResource(R.string.alarm_message, userName!!, medName)
                        } else {
                            stringResource(R.string.alarm_message_default, medName)
                        }
                        
                        Text(
                            text = message,
                            fontSize = 28.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 36.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )

                        Spacer(modifier = Modifier.height(48.dp))
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

    private fun startAlarm(savedRingtoneUri: String?) {
        val uri = savedRingtoneUri?.let { Uri.parse(it) } 
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            
        ringtone = RingtoneManager.getRingtone(applicationContext, uri).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            play()
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() {
        ringtone?.stop()
        vibrator?.cancel()
    }

    private fun dismissNotification(medId: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(medId)
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
