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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medreminder.data.local.room.Medication
import com.example.medreminder.ui.MedViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MedViewModel = viewModel()) {
    val medications by viewModel.medications.collectAsState()
    val firstDoseTime by viewModel.firstDoseTime.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Daily Schedule") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (medications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Please set up your therapeutic plan in Settings first.")
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Today's Plan",
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
                                Text("SET FIRST DOSE TIME")
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "First dose at: ${formatTime(firstDoseTime!!)}",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                TextButton(onClick = { viewModel.clearDay() }) {
                                    Text("RESET")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(text = "Schedule", style = MaterialTheme.typography.titleMedium)
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
                Text("Scheduled at: ${formatTime(scheduledTime)}")
            } else {
                Text("Waiting for first dose time")
            }
        },
        overlineContent = {
            if (medication.orderIndex > 0) {
                Text("+ ${medication.intervalHours}h ${medication.intervalMinutes}m from previous")
            } else {
                Text("First medication of the day")
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
