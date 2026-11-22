package pushtest.navigation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment

@Composable
fun VitalSignsScreen(
    onExit: () -> Unit
) {
    val vitalSigns = remember {
        mutableStateListOf<VitalSign>()
    }

    var showForm by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var selectedIndex by remember { mutableIntStateOf(-1) }

    if (showForm) {
        VitalSignForm(
            existingVital = vitalSigns.getOrNull(editingIndex),
            onSave = { vital ->
                if (editingIndex >= 0) {
                    vitalSigns[editingIndex] = vital
                } else {
                    vitalSigns.add(vital)
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
                text = "Vital Signs",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                        text = "All Vital Signs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (vitalSigns.isEmpty()) {
                        Text("No vital signs have been added yet.")
                    }

                    vitalSigns.forEachIndexed { index, vital ->
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
                                    text = vital.name,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "${vital.value} ${vital.unit}"
                                )

                                if (vital.notes.isNotBlank()) {
                                    Text(
                                        text = "Notes: ${vital.notes}"
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                Button(
                    onClick = {
                        if (selectedIndex >= 0) {
                            vitalSigns.removeAt(selectedIndex)
                            selectedIndex = -1
                        }
                    },
                    enabled = selectedIndex >= 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Delete")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share Vital")
            }
        }
    }
}

@Composable
private fun VitalSignForm(
    existingVital: VitalSign?,
    onSave: (VitalSign) -> Unit,
    onCancel: () -> Unit,
    onExit: () -> Unit
) {
    val vitalOptions = listOf(
        "Blood Pressure",
        "Glucose Level",
        "Cholesterol",
        "Body Temperature",
        "Respiratory Rate",
        "Pain Level",
        "Heart Rate",
        "Other"
    )

    fun defaultUnit(vital: String): String {
        return when (vital) {
            "Blood Pressure" -> "mmHg"
            "Glucose Level" -> "mg/dL"
            "Cholesterol" -> "mg/dL"
            "Body Temperature" -> "°C"
            "Respiratory Rate" -> "breaths/min"
            "Pain Level" -> "/10"
            "Heart Rate" -> "bpm"
            else -> ""
        }
    }

    val initialType = existingVital?.name?.let {
        if (it in vitalOptions) it else "Other"
    } ?: ""

    var selectedVital by remember(existingVital) {
        mutableStateOf(initialType)
    }

    var customName by remember(existingVital) {
        mutableStateOf(
            if (initialType == "Other")
                existingVital?.name ?: ""
            else ""
        )
    }

    var value by remember(existingVital) {
        mutableStateOf(existingVital?.value ?: "")
    }

    var unit by remember(existingVital) {
        mutableStateOf(
            existingVital?.unit ?: defaultUnit(initialType)
        )
    }

    var customUnitEnabled by remember(existingVital) {
        mutableStateOf(false)
    }

    var notes by remember(existingVital) {
        mutableStateOf(existingVital?.notes ?: "")
    }

    var expanded by remember { mutableStateOf(false) }

    val vitalName = if (selectedVital == "Other") {
        customName.trim()
    } else {
        selectedVital
    }

    val valid =
        selectedVital.isNotBlank() &&
                (selectedVital != "Other" || customName.isNotBlank()) &&
                value.isNotBlank() &&
                unit.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (existingVital == null)
                "Add Vital Sign"
            else
                "Edit Vital Sign",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Name of Vital",
            fontWeight = FontWeight.Bold
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    selectedVital.ifBlank {
                        "Select Vital Sign"
                    }
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                vitalOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selectedVital = option
                            unit = defaultUnit(option)
                            customUnitEnabled = false
                            value = ""
                            expanded = false
                        }
                    )
                }
            }
        }

        if (selectedVital == "Other") {
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                label = {
                    Text("Enter Name of Vital")
                },
                placeholder = {
                    Text("Enter a custom vital sign")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Text(
            "Vital Sign Measurement",
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Number / Value") },
                placeholder = { Text("Enter value") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            OutlinedTextField(
                value = unit,
                onValueChange = { unit = it },
                label = { Text("Unit") },
                placeholder = { Text("Unit") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                readOnly = !customUnitEnabled
            )
        }

        OutlinedButton(
            onClick = {
                customUnitEnabled = !customUnitEnabled

                if (!customUnitEnabled) {
                    unit = defaultUnit(selectedVital)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (customUnitEnabled)
                    "Use Suggested Unit"
                else
                    "Enter Custom Unit"
            )
        }

        Text(
            "Additional Notes (Optional)",
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = {
                Text("Additional Information")
            },
            placeholder = {
                Text("Enter any additional information here...")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (valid) {
                    onSave(
                        VitalSign(
                            name = vitalName,
                            value = value.trim(),
                            unit = unit.trim(),
                            notes = notes.trim()
                        )
                    )
                }
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Submit Vital")
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
}