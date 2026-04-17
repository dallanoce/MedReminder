package com.example.medreminder.ui.screens

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medreminder.data.local.room.Medication
import com.example.medreminder.ui.MedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationPlanScreen(viewModel: MedViewModel = viewModel()) {
    val medications by viewModel.medications.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Therapeutic Plan") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Medication")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (medications.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No medications added yet.")
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
                        text = "Interval: ${medication.intervalHours}h ${medication.intervalMinutes}m from previous",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = "First medication of the plan",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
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
        title = { Text(if (isFirst) "Add First Medication" else "Add Medication") },
        text = {
            Column {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medication Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (!isFirst) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        TextField(
                            value = hours,
                            onValueChange = { if (it.all { char -> char.isDigit() }) hours = it },
                            label = { Text("Hours") },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = minutes,
                            onValueChange = { if (it.all { char -> char.isDigit() }) minutes = it },
                            label = { Text("Minutes") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        text = "Interval from previous medication",
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
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
