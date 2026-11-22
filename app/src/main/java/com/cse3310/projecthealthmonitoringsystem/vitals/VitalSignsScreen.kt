package com.cse3310.projecthealthmonitoringsystem.vitals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation

data class VitalTypeOption(val id: Long, val name: String, val unit: String,
    val primaryValueLabel: String = "Reading", val secondValueLabel: String? = null,
    val normalMin: Double? = null, val normalMax: Double? = null,
    val secondaryMin: Double? = null, val secondaryMax: Double? = null)
data class VitalReadingItem(val typeName: String, val value: String, val unit: String,
    val secondValue: String? = null, val recordedAt: String, val isAbnormal: Boolean, val timestamp: Long = 0L)

@Composable
fun VitalSignsScreen(vitalTypes: List<VitalTypeOption>, readings: List<VitalReadingItem>,
    onSaveReading: (Long, Double, Double?) -> Unit, modifier: Modifier = Modifier,
    saving: Boolean = false, saveMessage: String? = null, savedVersion: Int = 0,
    onMessageShown: () -> Unit = {}, saveError: String? = null) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var entering by remember { mutableStateOf(false) }
    val selected = vitalTypes.firstOrNull { it.id == selectedId } ?: vitalTypes.firstOrNull()
    val filtered = readings.filter { it.typeName == selected?.name }
    val latest = filtered.maxByOrNull { it.timestamp }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(savedVersion) { if (savedVersion > 0) entering = false }
    LaunchedEffect(saveMessage) { saveMessage?.let { snackbar.showSnackbar(it); onMessageShown() } }
    Box(modifier.fillMaxSize()) {
        PhmsPage(Modifier.padding(bottom = 12.dp)) {
            PhmsScreenHeader("Vital signs", "Your readings, over time.", PhmsIcons.Vitals)
            Box {
                PhmsOutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(vitalIconFor(selected?.name.orEmpty()), null)
                    Text(selected?.name ?: "Choose a measurement", Modifier.weight(1f).padding(horizontal = 12.dp))
                    Icon(Icons.Outlined.ExpandMore, null)
                }
                DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                    vitalTypes.forEach { type ->
                        DropdownMenuItem(text = { Text(type.name) }, leadingIcon = { Icon(vitalIconFor(type.name), null) },
                            onClick = { selectedId = type.id; choosing = false })
                    }
                }
            }
            if (selected == null) {
                PhmsEmptyStateCard("No measurement types", "Add supported measurements in Care details.", PhmsIcons.Vitals)
            } else {
                PhmsCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PhmsIconBadge(vitalIconFor(selected.name))
                            Column(Modifier.weight(1f)) {
                                Text("Latest ${selected.name.lowercase()}", style = MaterialTheme.typography.bodyMedium)
                                Text(latest?.let { "${it.value}${it.secondValue?.let { v -> "/$v" } ?: ""} ${it.unit}" } ?: "No reading yet",
                                    style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        latest?.let {
                            Text(it.recordedAt, style = MaterialTheme.typography.bodyMedium)
                            if (it.isAbnormal) Text("Review your health alert", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge)
                        }
                        Button(onClick = { entering = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add reading")
                        }
                    }
                }
                if (filtered.isNotEmpty()) VitalHistoryChart(filtered, selected)
                PhmsSectionHeader("${selected.name} history")
                if (filtered.isEmpty()) Text("Add your first reading to start your history.", style = MaterialTheme.typography.bodyLarge)
                filtered.sortedByDescending { it.timestamp }.forEach { reading ->
                    ListItem(headlineContent = { Text("${reading.value}${reading.secondValue?.let { " / $it" } ?: ""} ${reading.unit}",
                        style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text(reading.recordedAt) },
                        leadingContent = { Icon(vitalIconFor(reading.typeName), null) },
                        trailingContent = { if (reading.isAbnormal) Icon(Icons.Outlined.WarningAmber, "Health alert", tint = MaterialTheme.colorScheme.error) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(48.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
    if (entering && selected != null) VitalEntryDialog(selected, saving, saveError, onDismiss = { entering = false }, onSave = onSaveReading)
}

@Composable
private fun VitalEntryDialog(type: VitalTypeOption, saving: Boolean, saveError: String?, onDismiss: () -> Unit,
    onSave: (Long, Double, Double?) -> Unit) {
    var first by remember(type.id) { mutableStateOf("") }
    var second by remember(type.id) { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    var verify by remember { mutableStateOf(false) }
    val value = first.toDoubleOrNull()
    val secondary = second.toDoubleOrNull()
    val issue = HealthValidation.vital(type.name, type.unit, value, secondary, type.secondValueLabel != null)
    val dismiss = rememberGuardedDismiss(first.isNotBlank() || second.isNotBlank(), saving, onDismiss)
    fun submit() {
        attempted = true
        if (issue != null) return
        val abnormal = type.normalMin?.let { value!! < it } == true || type.normalMax?.let { value!! > it } == true ||
            type.secondaryMin?.let { secondary != null && secondary < it } == true ||
            type.secondaryMax?.let { secondary != null && secondary > it } == true
        if (abnormal) verify = true else onSave(type.id, value!!, secondary)
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add ${type.name.lowercase()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                PhmsTextField(first, { first = it }, enabled = !saving,
                    label = { Text("${type.primaryValueLabel} (${type.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = attempted && issue != null && issue.field != "Diastolic",
                    supportingText = { if (attempted && issue != null && issue.field != "Diastolic") Text(issue.message) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (type.secondValueLabel != null) PhmsTextField(second, { second = it }, enabled = !saving,
                    label = { Text("${type.secondValueLabel} (${type.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = attempted && issue?.field == "Diastolic",
                    supportingText = { if (attempted && issue?.field == "Diastolic") Text(issue.message) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Check the units on your device before saving.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { PhmsTextButton(enabled = !saving, onClick = { submit() }) { Text(if (saving) "Saving…" else "Save reading") } },
        dismissButton = { PhmsTextButton(enabled = !saving, onClick = dismiss) { Text("Cancel") } })
    if (verify) AlertDialog(onDismissRequest = { verify = false }, title = { Text("Double-check this reading") },
        text = { Text("This reading is outside your saved reference range. You can still record it. A health alert may be created.") },
        confirmButton = { PhmsTextButton(enabled = !saving, onClick = { verify = false; onSave(type.id, value!!, secondary) }) { Text("Save reading") } },
        dismissButton = { PhmsTextButton(onClick = { verify = false }) { Text("Check reading") } })
}

internal fun vitalIconFor(name: String) = when {
    name.contains("temperature", true) -> Icons.Outlined.Thermostat
    name.contains("respiratory", true) -> Icons.Outlined.Air
    name.contains("oxygen", true) -> Icons.Outlined.Bloodtype
    name.contains("pain", true) -> Icons.Outlined.SentimentDissatisfied
    name.contains("glucose", true) -> PhmsIcons.Glucose
    name.contains("cholesterol", true) -> Icons.Outlined.Science
    name.contains("heart", true) -> Icons.Outlined.FavoriteBorder
    name.contains("pressure", true) -> PhmsIcons.BloodPressure
    else -> PhmsIcons.Vitals
}
