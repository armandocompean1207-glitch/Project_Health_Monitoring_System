package com.cse3310.projecthealthmonitoringsystem.medication

import android.Manifest
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.cse3310.projecthealthmonitoringsystem.ui.components.rememberGuardedDismiss
import com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsOutlinedButton as OutlinedButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextField as OutlinedTextField
import androidx.compose.material3.Text
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.dao.CandidateConflict
import com.cse3310.projecthealthmonitoringsystem.data.entity.AuditLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import com.cse3310.projecthealthmonitoringsystem.data.repository.MedicationRepository
import com.cse3310.projecthealthmonitoringsystem.monitoring.AlertHandoff
import com.cse3310.projecthealthmonitoringsystem.monitoring.AlertSender
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseAlarms
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import kotlinx.coroutines.launch

@Composable
fun MedicationScreen() {
    MedicationDesign { MedicationContent() }
}

@Composable
private fun MedicationContent() {
    val context = LocalContext.current
    val app = context.phms()
    val userId = CurrentUser.userId
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(context) { AlertSender.ensureChannel(context) }

    val medications by remember(userId) { app.medicationRepository.observeActiveMedications(userId) }.collectAsState(initial = emptyList())
    val doctors by remember(userId) { app.maintenanceRepository.observeDoctors(userId) }.collectAsState(initial = emptyList())
    val pharmacies by remember(userId) { app.maintenanceRepository.observePharmacies(userId) }.collectAsState(initial = emptyList())
    val today = remember { DoseTimes.todayRange() }
    val todayDoses by remember { app.medicationRepository.observeDoseLog(userId, today.first, today.second) }
        .collectAsState(initial = emptyList())

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var editing by remember { mutableStateOf<Medication?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var doseTimes by remember { mutableStateOf<List<Int?>>(listOf(null)) }
    var startDate by remember { mutableStateOf(MedicationTimes.dayStart(System.currentTimeMillis())) }
    var endDate by remember { mutableStateOf<Long?>(null) }
    var ongoing by remember { mutableStateOf(true) }
    var timesPerDay by remember { mutableStateOf(1) }
    var doctorId by remember { mutableStateOf<Long?>(null) }
    var pharmacyId by remember { mutableStateOf<Long?>(null) }
    var pharmacyTouched by remember { mutableStateOf(false) }
    var addingPharmacy by remember { mutableStateOf(false) }
    var addingProvider by remember { mutableStateOf(false) }
    var choosingProvider by remember { mutableStateOf(false) }
    var prescriptionNumber by remember { mutableStateOf("") }
    var refillsRemaining by remember { mutableStateOf("") }
    var nextRefillAt by remember { mutableStateOf<Long?>(null) }
    var treatmentDatesValid by remember { mutableStateOf(true) }
    var refillDateValid by remember { mutableStateOf(true) }
    androidx.compose.runtime.LaunchedEffect(pharmacies, showForm, editing) {
        if (showForm && editing == null && !pharmacyTouched && pharmacyId == null)
            pharmacyId = pharmacies.firstOrNull { it.isPreferred }?.pharmacyId
    }
    var formError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val dismissForm = rememberGuardedDismiss(showForm, saving || checking) { showForm = false }

    var pendingConflicts by remember { mutableStateOf<List<CandidateConflict>>(emptyList()) }
    var deleting by remember { mutableStateOf<Medication?>(null) }
    var historyFor by remember { mutableStateOf<Medication?>(null) }
    var history by remember { mutableStateOf<List<AuditLog>>(emptyList()) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun openForm(med: Medication?) {
        editing = med
        name = med?.name ?: ""
        dosage = med?.dosage ?: ""
        doseTimes = med?.let { MedicationTimes.forEditing(it) } ?: listOf(null)
        startDate = med?.startDate ?: MedicationTimes.dayStart(System.currentTimeMillis())
        endDate = med?.endDate
        ongoing = med?.endDate == null
        timesPerDay = med?.let { Frequency.timesPerDay(it.frequency) } ?: 1
        doctorId = med?.doctorId
        pharmacyId = if (med == null) pharmacies.firstOrNull { it.isPreferred }?.pharmacyId else med.pharmacyId
        pharmacyTouched = false
        prescriptionNumber = med?.prescriptionNumber.orEmpty()
        refillsRemaining = med?.refillsRemaining?.toString().orEmpty()
        nextRefillAt = med?.nextRefillAt
        treatmentDatesValid = true; refillDateValid = true
        formError = null
        showForm = true
    }

    fun validate(): String? =
        (if (!treatmentDatesValid || !refillDateValid) "Enter valid dates as MM/DD/YYYY." else null)
            ?: MedicationTimes.error(doseTimes, timesPerDay, startDate, if (ongoing) null else endDate, ongoing)
            ?: HealthValidation.medication(name, dosage, Frequency.label(timesPerDay), doseTimes.firstOrNull() ?: -1)
            ?: com.cse3310.projecthealthmonitoringsystem.validation.ProviderPharmacyValidation.refillInput(refillsRemaining)
            ?: com.cse3310.projecthealthmonitoringsystem.validation.ProviderPharmacyValidation.prescription(
                prescriptionNumber, refillsRemaining.toIntOrNull(), nextRefillAt)

    fun save(reportConflicts: Boolean) {
        if (saving) return
        formError = validate()
        if (formError != null) return
        val selectedTimes = doseTimes.filterNotNull().sorted()
        val minutes = selectedTimes.first()
        val encodedTimes = MedicationTimes.encode(selectedTimes)
        saving = true
        val current = editing
        scope.launch {
            try {
                if (current == null) {
                    val now = System.currentTimeMillis()
                    val med = Medication(
                        userId = userId,
                        name = name.trim(),
                        dosage = dosage.trim(),
                        frequency = Frequency.label(timesPerDay),
                        intakeTimeMinutes = minutes,
                        startDate = startDate,
                        endDate = if (ongoing) null else endDate,
                        doseTimesMinutes = encodedTimes,
                        doctorId = doctorId,
                        pharmacyId = pharmacyId,
                        prescriptionNumber = prescriptionNumber.trim().ifBlank { null },
                        refillsRemaining = refillsRemaining.toIntOrNull(),
                        nextRefillAt = nextRefillAt,
                        createdAt = now,
                        updatedAt = now
                    )
                    val id = app.medicationRepository.addMedication(userId, med)
                    if (id == null) {
                        toast("Could not save: check the selected provider and pharmacy.")
                        return@launch
                    }
                    DoseAlarms.scheduleNextReminder(context, med.copy(medicationId = id))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlertSender.canNotify(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    val updated = current.copy(
                        name = name.trim(),
                        dosage = dosage.trim(),
                        frequency = Frequency.label(timesPerDay),
                        intakeTimeMinutes = minutes,
                        startDate = startDate,
                        endDate = if (ongoing) null else endDate,
                        doseTimesMinutes = encodedTimes,
                        doctorId = doctorId,
                        pharmacyId = pharmacyId,
                        prescriptionNumber = prescriptionNumber.trim().ifBlank { null },
                        refillsRemaining = refillsRemaining.toIntOrNull(),
                        nextRefillAt = nextRefillAt
                    )
                    if (!app.medicationRepository.editMedication(userId, updated)) {
                        toast("Could not save the changes.")
                        return@launch
                    }
                    DoseAlarms.cancelReminder(context, current.medicationId)
                    DoseAlarms.scheduleNextReminder(context, updated)
                }
                if (reportConflicts) {
                    app.monitoringRepository.raiseConflictAlerts(userId, app.medicationRepository.detectConflicts(userId))
                        .filter { it > 0 }.forEach { alertId ->
                            app.monitoringRepository.getAlert(userId, alertId)?.let { AlertHandoff.send(context, it) }
                        }
                }
                toast("Medication saved")
                showForm = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                formError = e.message ?: "Could not save medication. Please try again."
            } finally { saving = false }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (showForm) com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsScreenHeader(
                if (showForm) if (editing == null) "Add medication" else "Edit medication" else "Medication",
                if (showForm) null else "Your medicines and daily schedule.",
                com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsIcons.Medication,
                if (showForm) dismissForm else null)
            else MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
                primaryContainer = androidx.compose.ui.graphics.Color(0xFF065F46),
                onPrimaryContainer = androidx.compose.ui.graphics.Color.White)) {
                com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsScreenHeader(
                    "Medication", "Your medicines and daily schedule.",
                    com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsIcons.Medication)
            }
        }

        if (!showForm) {
            item { Button(onClick = { openForm(null) }) { Text("Add medication") } }
        } else {
            item {
                if (addingPharmacy) com.cse3310.projecthealthmonitoringsystem.maintenance.PharmacyEditor(
                    com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy(userId = userId, name = ""),
                    onDismiss = { addingPharmacy = false },
                    onSaved = { pharmacyId = it; pharmacyTouched = true; addingPharmacy = false })
                else Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(name, { name = it }, label = { Text("Name") }, enabled = !saving && !checking,
                            isError = formError != null && HealthValidation.name(name) != null,
                            supportingText = { if (formError != null) HealthValidation.name(name)?.let { Text(it) } }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(dosage, { dosage = it }, label = { Text("Dose (for example, 5 mg)") }, enabled = !saving && !checking,
                            isError = formError != null && HealthValidation.medication(name, dosage, Frequency.label(timesPerDay), doseTimes.firstOrNull() ?: -1) != null,
                            singleLine = true, modifier = Modifier.fillMaxWidth())
                        MedicationTreatmentDates(startDate, endDate, ongoing, !saving && !checking,
                            onStart = { startDate = it }, onEnd = { endDate = it }, onOngoing = { ongoing = it },
                            showErrors = formError != null, onValidityChange = { treatmentDatesValid = it })
                        Text("How often")
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..6).forEach { n ->
                                FilterChip(selected = timesPerDay == n, enabled = !saving && !checking, onClick = {
                                    doseTimes = List(n) { index -> doseTimes.getOrNull(index) }
                                    timesPerDay = n
                                }, label = { Text("${n}x a day") })
                            }
                        }
                        MedicationDoseTimeFields(doseTimes, !saving && !checking, formError != null) { index, minutes ->
                            doseTimes = doseTimes.mapIndexed { position, old -> if (position == index) minutes else old }
                        }
                        Text("Prescribing provider (optional)")
                        androidx.compose.foundation.layout.Box {
                            OutlinedButton(enabled = !saving && !checking, modifier = Modifier.fillMaxWidth(),
                                onClick = { choosingProvider = true }) {
                                Text(doctors.firstOrNull { it.doctor.doctorId == doctorId }?.doctor?.name ?: "None",
                                    Modifier.weight(1f))
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.ExpandMore, "Choose provider")
                            }
                            androidx.compose.material3.DropdownMenu(choosingProvider, onDismissRequest = { choosingProvider = false }) {
                                androidx.compose.material3.DropdownMenuItem(text = { Text("None") },
                                    onClick = { doctorId = null; choosingProvider = false })
                                doctors.forEach { d ->
                                    androidx.compose.material3.DropdownMenuItem(text = { Text(d.doctor.name) },
                                        onClick = { doctorId = d.doctor.doctorId; choosingProvider = false })
                                }
                                androidx.compose.material3.DropdownMenuItem(text = { Text("+ Add provider") },
                                    onClick = { choosingProvider = false; addingProvider = true })
                            }
                        }
                        MedicationPrescriptionFields(pharmacies, pharmacyId, prescriptionNumber, refillsRemaining, nextRefillAt,
                            enabled = !saving && !checking,
                            onPharmacy = { pharmacyTouched = true; pharmacyId = it },
                            onRx = { prescriptionNumber = it }, onRefills = { refillsRemaining = it }, onDate = { nextRefillAt = it },
                            onAddPharmacy = { addingPharmacy = true }, onDateValidityChange = { refillDateValid = it })
                        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !saving && !checking, onClick = {
                                formError = validate()
                                if (formError == null) {
                                    val current = editing
                                    val nameChanged = current == null || !current.name.equals(name.trim(), ignoreCase = true)
                                    if (!nameChanged) {
                                        save(reportConflicts = false)
                                    } else {
                                        checking = true
                                        scope.launch {
                                            try {
                                                val conflicts = app.medicationRepository.detectConflict(
                                                    userId, name, current?.medicationId ?: 0)
                                                if (conflicts.isEmpty()) save(reportConflicts = false) else pendingConflicts = conflicts
                                            } catch (e: kotlinx.coroutines.CancellationException) { throw e
                                            } catch (e: Exception) { formError = "Couldn't check this medication. Please try again."
                                            } finally { checking = false }
                                        }
                                    }
                                }
                            }) { Text("Save") }
                            OutlinedButton(enabled = !saving && !checking, onClick = dismissForm) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        if (!showForm) item {
            MedicationOverview(
                onAdd = { openForm(null) },
                onEdit = { openForm(it) },
                onAudit = { med ->
                    scope.launch {
                        history = app.medicationRepository.getAuditLog(userId, med.medicationId)
                        historyFor = med
                    }
                },
                onDeactivate = { deleting = it }
            )
        }
        item { Text("") }
    }

    if (pendingConflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingConflicts = emptyList() },
            title = { Text("Possible medication conflict") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingConflicts.forEach {
                        Text("• ${it.conflictingMedicationName} (${it.severity}): ${it.description}")
                    }
                    Text(
                        "This check uses a sample table for the class project and is not medical advice.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingConflicts = emptyList()
                    save(reportConflicts = true)
                }) { Text("Save and notify contacts") }
            },
            dismissButton = { TextButton(onClick = { pendingConflicts = emptyList() }) { Text("Cancel") } }
        )
    }


    if (addingProvider) com.cse3310.projecthealthmonitoringsystem.maintenance.ProviderEditor(
        com.cse3310.projecthealthmonitoringsystem.data.entity.Doctor(userId = userId, name = ""),
        onDismiss = { addingProvider = false },
        onSaved = { doctorId = it; addingProvider = false })

    deleting?.let { med ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Deactivate ${med.name}?") },
            text = { Text("Future reminders will stop. The medication and all dose history will be retained. Existing pending doses keep their monitoring checks.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        val result = app.medicationRepository.deactivateMedication(userId, med.medicationId)
                        DoseAlarms.cancelReminder(context, med.medicationId)
                        toast(
                            if (result) "Medication deactivated. Past doses are kept." else "Medication not found"
                        )
                    }
                }) { Text("Deactivate") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    historyFor?.let { med ->
        AlertDialog(
            onDismissRequest = { historyFor = null },
            title = { Text("Change history: ${med.name}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (history.isEmpty()) Text("No changes since it was added.")
                    history.forEach {
                        Text("${DoseTimes.formatTime(it.changedAt)}: ${it.field} changed from \"${it.oldValue ?: ""}\" to \"${it.newValue ?: ""}\"")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyFor = null }) { Text("Close") } }
        )
    }
}
