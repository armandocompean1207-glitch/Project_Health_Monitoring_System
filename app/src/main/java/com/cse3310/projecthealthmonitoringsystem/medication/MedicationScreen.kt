package com.cse3310.projecthealthmonitoringsystem.medication

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val context = LocalContext.current
    val app = context.phms()
    val userId = CurrentUser.userId
    val scope = rememberCoroutineScope()
    remember { AlertSender.ensureChannel(context) }

    val medications by remember { app.medicationRepository.observeActiveMedications(userId) }.collectAsState(initial = emptyList())
    val doctors by remember { app.maintenanceRepository.observeDoctors(userId) }.collectAsState(initial = emptyList())
    val today = remember { DoseTimes.todayRange() }
    val todayDoses by remember { app.medicationRepository.observeDoseLog(userId, today.first, today.second) }
        .collectAsState(initial = emptyList())

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    var editing by remember { mutableStateOf<Medication?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var intakeTime by remember { mutableStateOf("08:00") }
    var timesPerDay by remember { mutableStateOf(1) }
    var doctorId by remember { mutableStateOf<Long?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    var pendingConflicts by remember { mutableStateOf<List<CandidateConflict>>(emptyList()) }
    var deleting by remember { mutableStateOf<Medication?>(null) }
    var historyFor by remember { mutableStateOf<Medication?>(null) }
    var history by remember { mutableStateOf<List<AuditLog>>(emptyList()) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun openForm(med: Medication?) {
        editing = med
        name = med?.name ?: ""
        dosage = med?.dosage ?: ""
        intakeTime = med?.let { DoseTimes.timeText(it.intakeTimeMinutes) } ?: "08:00"
        timesPerDay = med?.let { Frequency.timesPerDay(it.frequency) } ?: 1
        doctorId = med?.doctorId
        formError = null
        showForm = true
    }

    fun validate(): String? = when {
        name.isBlank() -> "Enter a medication name."
        dosage.isBlank() -> "Enter a dosage, for example 5 mg."
        DoseTimes.minutesOf(intakeTime) == null -> "Enter the intake time as HH:mm, for example 08:30."
        else -> null
    }

    fun save(reportConflicts: Boolean) {
        val minutes = DoseTimes.minutesOf(intakeTime) ?: return
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
                        startDate = now,
                        doctorId = doctorId,
                        createdAt = now,
                        updatedAt = now
                    )
                    val id = app.medicationRepository.addMedication(userId, med)
                    if (id == null) {
                        toast("Could not save: pick one of your doctors.")
                        return@launch
                    }
                    DoseAlarms.scheduleNextReminder(context, med.copy(medicationId = id))
                    if (reportConflicts) {
                        val pairs = app.medicationRepository.detectConflicts(userId)
                            .filter { it.medicationIdA == id || it.medicationIdB == id }
                        app.monitoringRepository.raiseConflictAlerts(userId, pairs)
                            .filter { it > 0 }
                            .forEach { alertId ->
                                app.monitoringRepository.getAlert(userId, alertId)?.let { AlertHandoff.send(context, it) }
                            }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !AlertSender.canNotify(context)) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    val updated = current.copy(
                        name = name.trim(),
                        dosage = dosage.trim(),
                        frequency = Frequency.label(timesPerDay),
                        intakeTimeMinutes = minutes,
                        doctorId = doctorId
                    )
                    if (!app.medicationRepository.editMedication(userId, updated)) {
                        toast("Could not save the changes.")
                        return@launch
                    }
                    DoseAlarms.cancelReminder(context, current.medicationId)
                    DoseAlarms.scheduleNextReminder(context, updated)
                }
                toast("Medication saved")
                showForm = false
            } catch (e: IllegalArgumentException) {
                formError = e.message
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Medication", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            Text("Your medications and dose reminders.", style = MaterialTheme.typography.bodyMedium)
        }

        if (!showForm) {
            item { Button(onClick = { openForm(null) }) { Text("Add medication") } }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (editing == null) "Add medication" else "Edit medication", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(dosage, { dosage = it }, label = { Text("Dosage (e.g. 5 mg)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(intakeTime, { intakeTime = it }, label = { Text("First intake time (HH:mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text("How often")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (1..4).forEach { n ->
                                FilterChip(selected = timesPerDay == n, onClick = { timesPerDay = n }, label = { Text("${n}x a day") })
                            }
                        }
                        Text("Prescribing doctor")
                        if (doctors.isEmpty()) {
                            Text("No doctors yet. Add them in Data Maintenance.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(selected = doctorId == null, onClick = { doctorId = null }, label = { Text("None") })
                                doctors.forEach { d ->
                                    FilterChip(
                                        selected = doctorId == d.doctor.doctorId,
                                        onClick = { doctorId = d.doctor.doctorId },
                                        label = { Text(d.doctor.name) }
                                    )
                                }
                            }
                        }
                        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                formError = validate()
                                if (formError == null) {
                                    val current = editing
                                    val nameChanged = current == null || !current.name.equals(name.trim(), ignoreCase = true)
                                    if (!nameChanged) {
                                        save(reportConflicts = false)
                                    } else {
                                        scope.launch {
                                            val conflicts = app.medicationRepository.detectConflict(
                                                userId, name, current?.medicationId ?: 0
                                            )
                                            if (conflicts.isEmpty()) save(reportConflicts = false) else pendingConflicts = conflicts
                                        }
                                    }
                                }
                            }) { Text("Save") }
                            OutlinedButton(onClick = { showForm = false }) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        item { Text("Today's doses", style = MaterialTheme.typography.titleLarge) }
        if (todayDoses.isEmpty()) {
            item { Text("No doses yet today. A dose appears here when its reminder goes off.") }
        }
        items(todayDoses, key = { "dose-${it.dose.doseId}" }) { row ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${row.medicationName} · ${row.dosage}", style = MaterialTheme.typography.titleMedium)
                        Text(DoseTimes.formatTime(row.dose.scheduledAt), style = MaterialTheme.typography.bodySmall)
                    }
                    if (row.dose.status == DoseStatus.SCHEDULED) {
                        Button(onClick = {
                            scope.launch {
                                if (app.medicationRepository.markDoseTaken(userId, row.dose.doseId)) {
                                    DoseAlarms.cancelMissedCheck(context, row.dose.doseId)
                                }
                            }
                        }) { Text("Mark taken") }
                    } else {
                        AssistChip(onClick = {}, label = { Text(if (row.dose.status == DoseStatus.TAKEN) "Taken" else "Missed") })
                    }
                }
            }
        }

        item { Text("My medications", style = MaterialTheme.typography.titleLarge) }
        if (medications.isEmpty()) {
            item { Text("No medications yet.") }
        }
        items(medications, key = { "med-${it.medicationId}" }) { med ->
            val doctorName = doctors.firstOrNull { it.doctor.doctorId == med.doctorId }?.doctor?.name
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${med.name} · ${med.dosage}", style = MaterialTheme.typography.titleMedium)
                    Text("${med.frequency}, starting ${DoseTimes.timeText(med.intakeTimeMinutes)}")
                    if (doctorName != null) Text("Prescribed by $doctorName")
                    Text("Next reminder: ${DoseTimes.formatTime(DoseAlarms.nextReminderTime(med))}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { openForm(med) }) { Text("Edit") }
                        TextButton(onClick = {
                            scope.launch {
                                history = app.medicationRepository.getAuditLog(userId, med.medicationId)
                                historyFor = med
                            }
                        }) { Text("History") }
                        TextButton(onClick = { deleting = med }) { Text("Delete") }
                    }
                }
            }
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
                }) { Text("Save and alert designees") }
            },
            dismissButton = { TextButton(onClick = { pendingConflicts = emptyList() }) { Text("Cancel") } }
        )
    }

    deleting?.let { med ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${med.name}?") },
            text = { Text("Its reminders will stop. If it already has doses, they stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        val result = app.medicationRepository.deleteMedication(userId, med.medicationId)
                        DoseAlarms.cancelReminder(context, med.medicationId)
                        toast(
                            when (result) {
                                MedicationRepository.DeleteResult.DELETED -> "Medication deleted"
                                MedicationRepository.DeleteResult.DEACTIVATED -> "Medication stopped. Past doses are kept."
                                MedicationRepository.DeleteResult.NOT_FOUND -> "Medication not found"
                            }
                        )
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    historyFor?.let { med ->
        AlertDialog(
            onDismissRequest = { historyFor = null },
            title = { Text("Change history: ${med.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
