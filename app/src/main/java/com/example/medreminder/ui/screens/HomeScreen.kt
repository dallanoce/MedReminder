package com.example.medreminder.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medreminder.R
import com.example.medreminder.data.local.room.Medication
import com.example.medreminder.ui.MedViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MedViewModel = viewModel()) {
    val medications by viewModel.medications.collectAsState()
    val firstDoseTime by viewModel.firstDoseTime.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.home_title)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val greeting = if (!userName.isNullOrEmpty()) {
                stringResource(R.string.home_greeting, userName!!)
            } else {
                stringResource(R.string.home_greeting_default)
            }
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (medications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.home_empty_state))
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.home_today_plan),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (firstDoseTime == null) {
                            Button(
                                onClick = {
                                    val calendar = Calendar.getInstance()
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            val selectedCalendar = Calendar.getInstance().apply {
                                                set(Calendar.HOUR_OF_DAY, hour)
                                                set(Calendar.MINUTE, minute)
                                                set(Calendar.SECOND, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }
                                            viewModel.setFirstDoseTime(selectedCalendar.timeInMillis)
                                        },
                                        calendar.get(Calendar.HOUR_OF_DAY),
                                        calendar.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.home_set_first_dose))
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.home_first_dose_at, formatTime(firstDoseTime!!)),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                TextButton(onClick = { viewModel.clearDay() }) {
                                    Text(stringResource(R.string.home_reset))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(text = stringResource(R.string.home_schedule_title), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(medications) { index, med ->
                        val scheduledTime = calculateScheduledTime(firstDoseTime, medications, index)
                        ScheduleItem(med, scheduledTime)
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleItem(medication: Medication, scheduledTime: Long?) {
    ListItem(
        headlineContent = { Text(medication.name) },
        supportingContent = {
            if (scheduledTime != null) {
                Text(stringResource(R.string.home_scheduled_at, formatTime(scheduledTime)))
            } else {
                Text(stringResource(R.string.home_waiting_first_dose))
            }
        },
        overlineContent = {
            if (medication.orderIndex > 0) {
                Text(stringResource(R.string.home_interval_from_prev, medication.intervalHours, medication.intervalMinutes))
            } else {
                Text(stringResource(R.string.home_first_med_day))
            }
        }
    )
}

fun calculateScheduledTime(firstDoseTime: Long?, medications: List<Medication>, index: Int): Long? {
    if (firstDoseTime == null) return null
    var currentTime = firstDoseTime
    for (i in 0..index) {
        if (i > 0) {
            val med = medications[i]
            currentTime += (med.intervalHours * 3600 + med.intervalMinutes * 60) * 1000
        }
    }
    return currentTime
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
