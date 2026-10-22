package io.github.dallanoce.medreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dallanoce.medreminder.R
import io.github.dallanoce.medreminder.data.local.room.Medication
import io.github.dallanoce.medreminder.ui.MedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationPlanScreen(viewModel: MedViewModel = viewModel()) {
    val medications by viewModel.medications.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.plan_title)) })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_med_desc))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (medications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.plan_empty_state))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(medications) { med ->
                        MedicationItem(med, onDelete = { viewModel.deleteMedication(med) })
                    }
                }
            }
        }

        if (showAddDialog) {
            AddMedicationDialog(
                isFirst = medications.isEmpty(),
                onDismiss = { showAddDialog = false },
                onConfirm = { name, hours, minutes ->
                    viewModel.addMedication(name, hours, minutes)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun MedicationItem(medication: Medication, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = medication.name, style = MaterialTheme.typography.titleLarge)
                if (medication.orderIndex > 0) {
                    Text(
                        text = stringResource(R.string.plan_interval_text, medication.intervalHours, medication.intervalMinutes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.plan_first_med_text),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_desc))
            }
        }
    }
}

@Composable
fun AddMedicationDialog(isFirst: Boolean, onDismiss: () -> Unit, onConfirm: (String, Int, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isFirst) R.string.add_first_med_title else R.string.add_med_title)) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.med_name_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isFirst) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        TextField(
                            value = hours,
                            onValueChange = { if (it.all { char -> char.isDigit() }) hours = it },
                            label = { Text(stringResource(R.string.hours_label)) },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = minutes,
                            onValueChange = { if (it.all { char -> char.isDigit() }) minutes = it },
                            label = { Text(stringResource(R.string.minutes_label)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = stringResource(R.string.interval_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val h = hours.toIntOrNull() ?: 0
                    val m = minutes.toIntOrNull() ?: 0
                    if (name.isNotBlank()) {
                        onConfirm(name, h, m)
                    }
                }
            ) {
                Text(stringResource(R.string.add_btn))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_btn))
            }
        }
    )
}
