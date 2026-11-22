package pushtest.navigation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun MedicationScreen(
    viewModel: MedicationViewModel,
    onExit: () -> Unit
) {
    val medications = viewModel.medications

    var showForm by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    if (showForm) {
        MedicationForm(
            existingMedication = medications.getOrNull(editingIndex),
            onSave = { medication ->
                if (editingIndex >= 0) {
                    viewModel.updateMedication(editingIndex, medication)
                } else {
                    viewModel.addMedication(medication)
                }

                showForm = false
                editingIndex = -1
                selectedIndex = -1
            },
            onCancel = {
                showForm = false
                editingIndex = -1
            },
            onExit = onExit
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Medication",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Medication list area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE5E7EB)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = "All Medications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (medications.isEmpty()) {
                        Text("No medications have been added yet.")
                    }

                    medications.forEachIndexed { index, medication ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                                .clickable {
                                    selectedIndex = index
                                },
                            colors = CardDefaults.cardColors(
                                containerColor =
                                    if (selectedIndex == index)
                                        Color(0xFFBBDEFB)
                                    else
                                        Color.White
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Text(
                                    medication.name,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${medication.dosage} ${medication.dosageUnit}"
                                )
                                Text(
                                    "Every ${medication.frequency} " +
                                            medication.frequencyUnit
                                )
                                Text(
                                    "${medication.intake} dose(s) per intake"
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Three action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        editingIndex = -1
                        showForm = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Add")
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = {
                        if (selectedIndex >= 0) {
                            editingIndex = selectedIndex
                            showForm = true
                        }
                    },
                    enabled = selectedIndex >= 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Edit")
                }

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = {
                        if (selectedIndex >= 0) {
                            medications.removeAt(selectedIndex)
                            selectedIndex = -1
                        }
                    },
                    enabled = selectedIndex >= 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete")
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationForm(
    existingMedication: Medication?,
    onSave: (Medication) -> Unit,
    onCancel: () -> Unit,
    onExit: () -> Unit
) {
    var name by remember(existingMedication) {
        mutableStateOf(existingMedication?.name ?: "")
    }

    var dosage by remember(existingMedication) {
        mutableStateOf(existingMedication?.dosage ?: "")
    }

    var dosageUnit by remember(existingMedication) {
        mutableStateOf(existingMedication?.dosageUnit ?: "")
    }

    var frequency by remember(existingMedication) {
        mutableStateOf(existingMedication?.frequency ?: "")
    }

    var frequencyUnit by remember(existingMedication) {
        mutableStateOf(existingMedication?.frequencyUnit ?: "")
    }

    var intake by remember(existingMedication) {
        mutableStateOf(existingMedication?.intake ?: "")
    }

    var instructions by remember(existingMedication) {
        mutableStateOf(existingMedication?.instructions ?: "")
    }

    var startDate by remember(existingMedication) {
        mutableStateOf(existingMedication?.startDate ?: LocalDate.now())
    }


    var endDate by remember(existingMedication) {
        mutableStateOf(existingMedication?.endDate)
    }
    var firstDoseTime by remember(existingMedication) {
        mutableStateOf(
            existingMedication?.firstDoseTime ?: LocalTime.now().withSecond(0).withNano(0)
        )
    }

    var showTimePicker by remember {
        mutableStateOf(false)
    }

    val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")

    val valid =
        name.isNotBlank() &&
                dosage.toDoubleOrNull()?.let { it > 0 } == true &&
                dosageUnit.isNotBlank() &&
                frequency.toIntOrNull()?.let { it > 0 } == true &&
                frequencyUnit.isNotBlank() &&
                intake.toIntOrNull()?.let { it > 0 } == true &&
                instructions.isNotBlank() &&
                (endDate == null || !endDate!!.isBefore(startDate))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (existingMedication == null)
                "Add Medication" else "Edit Medication",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // Medication name
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Medication Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Dosage
        Text("Dosage", fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("Amount") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            UnitDropdown(
                label = "Unit",
                selected = dosageUnit,
                options = listOf("mg", "mcg", "g", "mL", "units"),
                onSelected = { dosageUnit = it },
                modifier = Modifier.weight(1f)
            )
        }

        // Frequency
        Text("Frequency", fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = frequency,
                onValueChange = { frequency = it },
                label = { Text("Every") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            UnitDropdown(
                label = "Time Period",
                selected = frequencyUnit,
                options = listOf(
                    "Hours", "Days", "Weeks",
                    "Months", "Years"
                ),
                onSelected = { frequencyUnit = it },
                modifier = Modifier.weight(1f)
            )
        }

        // Intake
        OutlinedTextField(
            value = intake,
            onValueChange = { intake = it },
            label = { Text("Number of doses per intake") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Start date
        Text("Start Date", fontWeight = FontWeight.Bold)

        OutlinedButton(
            onClick = { showStartPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(startDate.format(dateFormatter))
        }

        // End date
        Text("End Date (Optional)", fontWeight = FontWeight.Bold)

        OutlinedButton(
            onClick = { showEndPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                endDate?.format(dateFormatter) ?: "Select End Date"
            )
        }

        if (endDate != null) {
            TextButton(onClick = { endDate = null }) {
                Text("Clear End Date")
            }
        }
        // First Dose Time
        Text(
            "First Dose Time",
            fontWeight = FontWeight.Bold
        )

        OutlinedButton(
            onClick = {
                showTimePicker = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(firstDoseTime.format(timeFormatter))
        }

        Text(
            "This is the time your first medication dose is scheduled. " +
                    "Future doses will be calculated automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Instructions
        OutlinedTextField(
            value = instructions,
            onValueChange = { instructions = it },
            label = { Text("Additional Instructions") },
            placeholder = {
                Text("Enter instructions for taking this medication")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 5
        )

        // Action buttons
        Button(
            onClick = {
                if (valid) {
                    onSave(
                        Medication(
                            name = name.trim(),
                            dosage = dosage,
                            dosageUnit = dosageUnit,
                            frequency = frequency,
                            frequencyUnit = frequencyUnit,
                            intake = intake,
                            startDate = startDate,
                            firstDoseTime = firstDoseTime,
                            endDate = endDate,
                            instructions = instructions.trim()
                        )
                    )
                }
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Medication")
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }

        TextButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Exit to Home")
        }
    }

    // Start date calendar
    if (showStartPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = startDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            startDate = Instant.ofEpochMilli(it)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showStartPicker = false
                    }
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    // End date calendar
    if (showEndPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = endDate?.atStartOfDay(
                ZoneOffset.UTC
            )?.toInstant()?.toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let {
                            endDate = Instant.ofEpochMilli(it)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        }
                        showEndPicker = false
                    }
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

// First Dose Time Picker
    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = firstDoseTime.hour,
            initialMinute = firstDoseTime.minute,
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = {
                showTimePicker = false
            },
            title = {
                Text("Select First Dose Time")
            },
            text = {
                TimePicker(state = timeState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        firstDoseTime = LocalTime.of(
                            timeState.hour,
                            timeState.minute
                        )
                        showTimePicker = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}