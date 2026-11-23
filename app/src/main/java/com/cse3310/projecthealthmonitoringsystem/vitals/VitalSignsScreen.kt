package com.cse3310.projecthealthmonitoringsystem.vitals

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
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
import com.cse3310.projecthealthmonitoringsystem.validation.GlucoseContext
import com.cse3310.projecthealthmonitoringsystem.validation.VitalAssessment
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType

data class VitalTypeOption(val id: Long, val name: String, val unit: String,
    val primaryValueLabel: String = "Reading", val secondValueLabel: String? = null,
    val normalMin: Double? = null, val normalMax: Double? = null,
    val secondaryMin: Double? = null, val secondaryMax: Double? = null)
data class VitalEntryRequest(val typeId: Long, val value: Double, val second: Double?, val unit: String,
    val recordedAt: Long, val context: GlucoseContext)
data class VitalReadingItem(val typeName: String, val value: String, val unit: String,
    val secondValue: String? = null, val recordedAt: String, val isAbnormal: Boolean, val timestamp: Long = 0L,
    val contextLabel: String? = null, val readingId: Long = 0L,
    val primaryNumeric: Double? = null, val secondaryNumeric: Double? = null)

@Composable
fun VitalSignsScreen(vitalTypes: List<VitalTypeOption>, readings: List<VitalReadingItem>,
    onSaveReading: (VitalEntryRequest) -> Unit, modifier: Modifier = Modifier,
    saving: Boolean = false, saveMessage: String? = null, savedVersion: Int = 0,
    onMessageShown: () -> Unit = {}, saveError: String? = null, patientName: String = "") {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var entering by remember { mutableStateOf(false) }
    var trendId by rememberSaveable { mutableStateOf<Long?>(null) }
    var historyId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reporting by rememberSaveable { mutableStateOf(false) }
    val selectableTypes = vitalTypes.filter { supportedVital(it.name) }
    val selected = selectableTypes.firstOrNull { it.id == selectedId } ?: selectableTypes.firstOrNull()
    var legacyExpanded by remember { mutableStateOf(false) }
    val legacy = readings.filter { !supportedVital(it.typeName) }
    val filtered = readings.filter { it.typeName == selected?.name }
    val latest = latestRecordedReading(filtered)
    val trendType = selectableTypes.firstOrNull { it.id == trendId }
    val historyType = selectableTypes.firstOrNull { it.id == historyId }
    BackHandler(enabled = (historyType != null || reporting || trendType != null) && !entering) {
        if (historyType != null) historyId = null else if (reporting) reporting = false else trendId = null
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(savedVersion) { if (savedVersion > 0) entering = false }
    LaunchedEffect(saveMessage) { saveMessage?.let { snackbar.showSnackbar(it); onMessageShown() } }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onSurface = Color(0xFF111827),
        onSurfaceVariant = Color(0xFF111827), onBackground = Color(0xFF111827))) {
    Box(modifier.fillMaxSize()) {
        if (historyType != null) {
            VitalReadingHistory(historyType, readings.filter { it.typeName == historyType.name }) { historyId = null }
        } else if (reporting) {
            HealthReportScreen(patientName, selectableTypes, readings) { reporting = false }
        } else if (trendType != null) {
            VitalTrendDetail(trendType, readings.filter { it.typeName == trendType.name },
                onBack = { trendId = null }, onAdd = { entering = true }, onSeeAll = { historyId = trendType.id })
        } else PhmsPage(Modifier.padding(bottom = 12.dp)) {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDF48),
                onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF111827))) {
                PhmsScreenHeader("Vital signs", "Your readings, over time.", PhmsIcons.Vitals)
            }
            PhmsOutlinedButton(onClick = { reporting = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Description, null); Spacer(Modifier.width(8.dp)); Text("Generate health report")
            }
            PhmsSectionHeader("Readings")
            if (selected == null) {
                PhmsEmptyStateCard("No measurement types", "Add supported measurements in Providers & pharmacies.", PhmsIcons.Vitals)
            } else {
                PhmsCard(colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            PhmsOutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(vitalIconFor(selected.name), null)
                                Text(selected.name, Modifier.weight(1f).padding(horizontal = 12.dp))
                                Icon(Icons.Outlined.ExpandMore, null)
                            }
                            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                                selectableTypes.forEach { type ->
                                    DropdownMenuItem(text = { Text(type.name) }, leadingIcon = { Icon(vitalIconFor(type.name), null) },
                                        onClick = { selectedId = type.id; choosing = false })
                                }
                            }
                        }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primaryContainer = Color(0xFFFFDF48), onPrimaryContainer = Color.Black)) {
                                            PhmsIconBadge(vitalIconFor(selected.name))
                                        }
                            Column(Modifier.weight(1f)) {
                                Text("Latest ${selected.name.lowercase()}", style = MaterialTheme.typography.bodyMedium)
                                Text(latest?.let { "${it.value}${it.secondValue?.let { v -> "/$v" } ?: ""} ${it.unit}" } ?: "No reading yet",
                                    style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        latest?.let {
                            Text(it.recordedAt, style = MaterialTheme.typography.bodyMedium)
                            readingComparison(filtered, it)?.let { comparison -> Text(comparison, style = MaterialTheme.typography.bodyMedium) }
                            if (it.isAbnormal) Text("Review your health alert", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge)
                        }
                        Button(onClick = { entering = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add reading")
                        }
                    }
                }
                ReadingHistoryPreview(selected, filtered) { historyId = selected.id }
            }
            if (legacy.isNotEmpty()) {
                PhmsTextButton(onClick = { legacyExpanded = !legacyExpanded }) {
                    Text(if (legacyExpanded) "Hide earlier measurements" else "View earlier measurements")
                }
                if (legacyExpanded) {
                    PhmsSectionHeader("Earlier measurements")
                    legacy.sortedByDescending { it.timestamp }.forEach {
                        ListItem(headlineContent = { Text("${it.typeName}: ${it.value} ${it.unit}") },
                            supportingContent = { Text(it.recordedAt) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            HealthTrends(selectableTypes, readings) { trendId = it.id }
            Spacer(Modifier.height(48.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).imePadding())
    }
    val entryType = trendType ?: selected
    if (entering && entryType != null) VitalEntryDialog(entryType, saving, saveError, onDismiss = { entering = false }, onSave = onSaveReading)
    }
}

@Composable
private fun HealthTrends(types: List<VitalTypeOption>, readings: List<VitalReadingItem>,
    onOpen: (VitalTypeOption) -> Unit) {
    PhmsSectionHeader("Health trends")
    types.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { type ->
                val history = readings.filter { it.typeName == type.name }
                val latest = latestRecordedReading(history)
                val points = chartReadings(history, 90, System.currentTimeMillis())
                OutlinedCard(onClick = { onOpen(type) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE8E1D8)),
                    colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
                            primaryContainer = Color(0xFFFFDF48), onPrimaryContainer = Color.Black)) {
                            PhmsIconBadge(vitalIconFor(type.name))
                        }
                        Text(type.name, modifier = Modifier.heightIn(min = 48.dp),
                            style = MaterialTheme.typography.titleMedium)
                        if (points.isNotEmpty()) VitalTrendPlot(points, type, Modifier.fillMaxWidth().height(60.dp))
                        else Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                            Text("No trend yet", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(latest?.let(::readingText) ?: "No readings yet",
                            style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("View trend", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun VitalTrendDetail(type: VitalTypeOption, readings: List<VitalReadingItem>,
    onBack: () -> Unit, onAdd: () -> Unit, onSeeAll: () -> Unit) {
    val latest = latestRecordedReading(readings)
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        background = Color.White, surface = Color.White, surfaceContainer = Color.White,
        primaryContainer = Color(0xFFFFDF48), onPrimaryContainer = Color.Black)) {
        Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
            PhmsPage {
                PhmsScreenHeader(type.name, icon = vitalIconFor(type.name), onBack = onBack)
                Text("Latest reading", style = MaterialTheme.typography.bodyMedium)
                Text(latest?.let(::readingText) ?: "No reading yet", style = MaterialTheme.typography.headlineMedium)
                latest?.let {
                    Text(it.recordedAt, style = MaterialTheme.typography.bodyMedium)
                    readingComparison(readings, it)?.let { comparison -> Text(comparison, style = MaterialTheme.typography.bodyMedium) }
                }
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Add reading")
                }
                VitalHistoryChart(readings, type)
                ReadingHistoryPreview(type, readings, onSeeAll)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VitalEntryDialog(type: VitalTypeOption, saving: Boolean, saveError: String?, onDismiss: () -> Unit,
    onSave: (VitalEntryRequest) -> Unit) {
    var first by remember(type.id) { mutableStateOf("") }
    var second by remember(type.id) { mutableStateOf("") }
    var attempted by remember { mutableStateOf(false) }
    var recordedAt by remember(type.id) { mutableLongStateOf(System.currentTimeMillis()) }
    var recordedDateValid by remember { mutableStateOf(true) }
    var pending by remember { mutableStateOf<VitalEntryRequest?>(null) }
    var context by remember(type.id) { mutableStateOf(GlucoseContext.SAVED_RANGE) }
    var chooseContext by remember { mutableStateOf(false) }
    val reference = VitalType(type.id, type.name, type.unit, type.normalMin ?: -Double.MAX_VALUE,
        type.normalMax ?: Double.MAX_VALUE, type.secondValueLabel, type.secondaryMin, type.secondaryMax)
    val value = first.toDoubleOrNull()
    val secondary = second.toDoubleOrNull()
    val issue = HealthValidation.vital(type.name, type.unit, value, secondary, type.secondValueLabel != null)
    val dismiss = rememberGuardedDismiss(first.isNotBlank() || second.isNotBlank(), saving, onDismiss)
    fun submit() {
        attempted = true
        if (issue != null || !recordedDateValid) return
        val request = VitalEntryRequest(type.id, value!!, secondary, type.unit, recordedAt, context)
        if (VitalAssessment.isAbnormal(reference, value, secondary, context)) pending = request else onSave(request)
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
                if (type.name.contains("glucose", true)) {
                    Box {
                        PhmsOutlinedButton(enabled = !saving, onClick = { chooseContext = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(context.label)
                        }
                        DropdownMenu(expanded = chooseContext, onDismissRequest = { chooseContext = false }) {
                            GlucoseContext.entries.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) },
                                    onClick = { context = option; chooseContext = false })
                            }
                        }
                    }
                    Text(VitalAssessment.contextHelp(context), style = MaterialTheme.typography.bodyMedium)
                }
                PhmsDateInput(recordedAt, { it?.let { time -> recordedAt = time } }, "Date recorded",
                    minDate = 1, maxDate = System.currentTimeMillis(), enabled = !saving, onValidityChange = { recordedDateValid = it })
                Text("Check the units on your device before saving.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { PhmsTextButton(enabled = !saving && recordedDateValid, onClick = { submit() }) { Text(if (saving) "Saving…" else "Save reading") } },
        dismissButton = { PhmsTextButton(enabled = !saving, onClick = dismiss) { Text("Cancel") } })
    pending?.let { request ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("Double-check this reading") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(VitalAssessment.reading(type.name, request.unit, request.value, request.second))
                    Text("This reading is outside the selected reference range. Check it before saving. A health alert will be created.")
                    VitalAssessment.urgentAdvice(type.name, request.unit, request.value, request.second)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { PhmsTextButton(enabled = !saving, onClick = { pending = null; onSave(request) }) { Text("Save reading") } },
            dismissButton = { PhmsTextButton(onClick = { pending = null }) { Text("Check reading") } })
    }
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
