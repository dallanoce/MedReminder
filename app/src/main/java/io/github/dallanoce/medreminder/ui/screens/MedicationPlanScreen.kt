package io.github.dallanoce.medreminder.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.dallanoce.medreminder.R
import io.github.dallanoce.medreminder.data.local.room.Medication
import io.github.dallanoce.medreminder.ui.InputValidation
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
                    itemsIndexed(medications, key = { _, med -> med.id }) { index, med ->
                        MedicationItem(med, isFirst = index == 0, onDelete = { viewModel.deleteMedication(med) })
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
fun MedicationItem(medication: Medication, isFirst: Boolean, onDelete: () -> Unit) {
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
                if (!isFirst) {
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

    val nameTooLong = name.trim().length > InputValidation.MAX_NAME_LENGTH
    // The first medication has no interval (it's taken at the first dose time)
    val interval = if (isFirst) 0 to 0 else InputValidation.parseInterval(hours, minutes)
    val canAdd = InputValidation.isValidMedicationName(name) && interval != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isFirst) R.string.add_first_med_title else R.string.add_med_title)) },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.med_name_label)) },
                    singleLine = true,
                    isError = nameTooLong,
                    supportingText = if (nameTooLong) {
                        { Text(stringResource(R.string.error_med_name_length, InputValidation.MAX_NAME_LENGTH)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isFirst) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        TextField(
                            value = hours,
                            onValueChange = { hours = InputValidation.digitsOnly(it) },
                            label = { Text(stringResource(R.string.hours_label)) },
                            singleLine = true,
                            isError = interval == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = minutes,
                            onValueChange = { minutes = InputValidation.digitsOnly(it) },
                            label = { Text(stringResource(R.string.minutes_label)) },
                            singleLine = true,
                            isError = interval == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = stringResource(if (interval == null) R.string.error_interval else R.string.interval_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (interval == null) MaterialTheme.colorScheme.error else Color.Unspecified,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    interval?.let { (h, m) -> onConfirm(name.trim(), h, m) }
                },
                enabled = canAdd
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
