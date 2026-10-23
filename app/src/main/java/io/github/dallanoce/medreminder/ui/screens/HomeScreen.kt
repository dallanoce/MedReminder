package io.github.dallanoce.medreminder.ui.screens

import android.app.TimePickerDialog
import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dallanoce.medreminder.R
import io.github.dallanoce.medreminder.alarm.DoseSchedule
import io.github.dallanoce.medreminder.data.local.room.Medication
import io.github.dallanoce.medreminder.permissions.AlertPermission
import io.github.dallanoce.medreminder.ui.MedViewModel
import io.github.dallanoce.medreminder.ui.dayOffset
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MedViewModel = viewModel(),
    missingPermissions: List<AlertPermission> = emptyList(),
    onFixPermissions: () -> Unit = {}
) {
    val medications by viewModel.medications.collectAsState()
    val firstDoseTime by viewModel.firstDoseTime.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = { Text(stringResource(R.string.reset_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearDay()
                    showResetDialog = false
                }) {
                    Text(stringResource(R.string.reset_confirm_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_notification),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.home_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )
        }
    ) { padding ->
        // Same calculation the alarms are scheduled from
        val doses = remember(firstDoseTime, medications) {
            firstDoseTime?.let { DoseSchedule.doses(it, medications) }
        }
        // A single scrolling list, so everything stays reachable with large system fonts.
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            if (missingPermissions.isNotEmpty()) {
                item {
                    PermissionWarning(onFixPermissions)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item {
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
            }

            if (medications.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .fillParentMaxHeight(0.6f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.home_empty_state))
                    }
                }
            } else {
                item {
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
                                            DateFormat.is24HourFormat(context)
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
                                        text = stringResource(R.string.home_first_dose_at, formatTimeWithDay(firstDoseTime!!)),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(
                                        onClick = { showResetDialog = true },
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text(stringResource(R.string.home_reset))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = stringResource(R.string.home_schedule_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                itemsIndexed(medications, key = { _, med -> med.id }) { index, med ->
                    val scheduledTime = doses?.getOrNull(index)?.time
                    ScheduleItem(med, scheduledTime, isFirst = index == 0)
                    if (index < medications.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionWarning(onFix: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.home_perm_banner),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onFix) {
                Text(stringResource(R.string.home_perm_banner_fix))
            }
        }
    }
}

@Composable
fun ScheduleItem(medication: Medication, scheduledTime: Long?, isFirst: Boolean) {
    ListItem(
        headlineContent = { Text(medication.name) },
        supportingContent = {
            if (scheduledTime != null) {
                Text(stringResource(R.string.home_scheduled_at, formatTimeWithDay(scheduledTime)))
            } else {
                Text(stringResource(R.string.home_waiting_first_dose))
            }
        },
        overlineContent = {
            if (!isFirst) {
                Text(stringResource(R.string.home_interval_from_prev, medication.intervalHours, medication.intervalMinutes))
            } else {
                Text(stringResource(R.string.home_first_med_day))
            }
        }
    )
}

/** The time, marked "(tomorrow)" / "(yesterday)" when the schedule crosses midnight. */
@Composable
private fun formatTimeWithDay(timestamp: Long): String {
    val context = LocalContext.current
    val time = formatTime(context, timestamp)
    return when (dayOffset(timestamp, System.currentTimeMillis())) {
        0 -> time
        1 -> stringResource(R.string.time_tomorrow, time)
        -1 -> stringResource(R.string.time_yesterday, time)
        else -> DateFormat.getDateFormat(context).format(Date(timestamp)) + " " + time
    }
}

/** The time in the user's 12/24-hour setting and locale. */
fun formatTime(context: Context, timestamp: Long): String =
    DateFormat.getTimeFormat(context).format(Date(timestamp))
