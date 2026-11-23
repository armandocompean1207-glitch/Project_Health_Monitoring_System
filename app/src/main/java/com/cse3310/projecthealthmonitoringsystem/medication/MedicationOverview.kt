package com.cse3310.projecthealthmonitoringsystem.medication

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as OutlinedCard
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card

import android.app.DatePickerDialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.*
import com.cse3310.projecthealthmonitoringsystem.monitoring.*
import com.cse3310.projecthealthmonitoringsystem.data.repository.MonitoringRepository
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Schedule

private val MedicationTakenGreen = Color(0xFF166534)

@Composable
private fun TakenIntakeStatus(recordedAt: Long?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = CircleShape, color = Color(0xFFDCFCE7), contentColor = MedicationTakenGreen) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp))
                Text("Taken", style = MaterialTheme.typography.labelLarge)
            }
        }
        recordedAt?.let { Text("Taken on ${intakeDateTime(it)}", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun MedicationOverview(onAdd: () -> Unit, onEdit: (Medication) -> Unit, onAudit: (Medication) -> Unit,
    onDeactivate: (Medication) -> Unit) {
    val context = LocalContext.current
    val app = context.phms()
    val userId = CurrentUser.userId
    val scope = rememberCoroutineScope()
    val providers by remember(userId) { app.maintenanceRepository.observeDoctors(userId) }.collectAsState(emptyList())
    val pharmacies by remember(userId) { app.maintenanceRepository.observePharmacies(userId) }.collectAsState(emptyList())
    val all by remember(userId) { app.medicationRepository.observeAllMedications(userId) }.collectAsState(emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var historyDay by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var customHistoryDay by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var entry by remember(userId) { mutableStateOf<DoseEntry?>(null) }
    var entryHistory by remember { mutableStateOf<List<DoseCorrection>?>(null) }
    var originalEntry by remember { mutableStateOf<DoseLog?>(null) }
    LaunchedEffect(userId) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L - now % 1_000L)
        }
    }
    LaunchedEffect(userId) {
        while (true) {
            try {
                app.monitoringRepository.checkMissedDoses(userId).forEach { AlertHandoff.send(context, it) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = "Couldn't refresh missed-dose alerts." }
            delay(30_000)
        }
    }
    val range = DoseTimes.todayRange(if (tab == 2 && customHistoryDay) historyDay else now)
    val doses by remember(userId, range) { app.medicationRepository.observeDoseLog(userId, range.first, range.second) }.collectAsState(emptyList())
    val corrections by remember(userId, range) {
        app.medicationRepository.observeIntakeCorrections(userId, range.first, range.second)
    }.collectAsState(emptyList())
    val latestCorrections = corrections.associateBy { it.doseId }
    fun showHistory(doseId: Long) {
        scope.launch {
            try {
                originalEntry = app.medicationRepository.originalDose(userId, doseId)
                entryHistory = app.medicationRepository.intakeCorrectionHistory(userId, doseId)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Couldn't load intake history." }
        }
    }
    val todayRange = DoseTimes.todayRange(now)
    val todayDoses by remember(userId, todayRange) { app.medicationRepository.observeDoseLog(userId, todayRange.first, todayRange.second) }.collectAsState(emptyList())
    val active = remember(all) { all.filter { it.isActive } }
    val planned = remember(active, todayRange) { active.flatMap { med ->
        scheduledIntakes(med, todayRange.first, todayRange.second).map { med.medicationId to it }
    } }
    val day = medicationDayProgress(planned, todayDoses.map { it.dose }, active.map { it.medicationId }.toSet(), now)
    val count = day.total

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today", "Medicines", "History").forEachIndexed { index, label ->
                FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface))
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        MedicationReminderCard(day)
        if (tab == 0) {
            PhmsSectionHeader("Today's schedule")
            com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsOutlinedButton(enabled = !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        app.monitoringRepository.checkMissedDoses(userId).forEach { AlertHandoff.send(context, it) }
                    } catch (cancelled: CancellationException) { throw cancelled
                    } catch (_: Exception) { error = "Couldn't review recorded doses."
                    } finally { busy = false }
                }
            }) { Text(if (busy) "Checking…" else "Review recorded doses") }
            if (count == 0) PhmsEmptyStateCard("No doses scheduled today", "Add a medication to see its schedule and reminders.",
                PhmsIcons.Medication)
            val keys = (planned + todayDoses.map { it.dose.medicationId to it.dose.scheduledAt }).distinct().sortedBy { it.second }
            keys.forEach { (medId, at) ->
                val row = todayDoses.firstOrNull { it.dose.medicationId == medId && it.dose.scheduledAt == at }
                val med = all.firstOrNull { it.medicationId == medId }
                val status = row?.dose?.status
                val state = medicationIntakeState(status, at, now, med?.isActive == true)
                val label = state.label
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(DoseTimes.displayClock(Calendar.getInstance().apply { timeInMillis = at }.let {
                                    it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }), style = MaterialTheme.typography.labelMedium)
                                Surface(shape = CircleShape, color = MedicationYellow, modifier = Modifier.size(40.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(when (status) {
                                            DoseStatus.TAKEN -> Icons.Outlined.CheckCircle
                                            DoseStatus.MISSED -> Icons.Outlined.Cancel
                                            else -> Icons.Outlined.Schedule
                                        }, label, modifier = Modifier.size(24.dp),
                                            tint = when (status) {
                                                DoseStatus.MISSED -> MaterialTheme.colorScheme.error
                                                DoseStatus.TAKEN -> MedicationTakenGreen
                                                else -> MedicationInk
                                            })
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(row?.medicationName ?: med?.name ?: "Medication", style = MaterialTheme.typography.titleMedium)
                                Text(row?.dosage ?: med?.dosage.orEmpty())
                            }
                            if (state != MedicationIntakeState.TAKEN) Text(label, style = MaterialTheme.typography.labelLarge,
                                color = if (status == DoseStatus.MISSED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                        if (state == MedicationIntakeState.NOT_RECORDED) Text("No intake was recorded for this time.", style = MaterialTheme.typography.bodyMedium)
                        if (state == MedicationIntakeState.OVERDUE) Text("Checking missed-dose status…", style = MaterialTheme.typography.bodyMedium)
                        val revision = row?.dose?.doseId?.let { latestCorrections[it]?.correctionId } ?: 0L
                        val final = status in listOf(DoseStatus.TAKEN, DoseStatus.SKIPPED, DoseStatus.MISSED)
                        if (state == MedicationIntakeState.TAKEN) TakenIntakeStatus(row?.dose?.recordedAt)
                        if (state != MedicationIntakeState.TAKEN && state != MedicationIntakeState.SKIPPED) {
                            Button(enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MedicationYellow, contentColor = MedicationInk),
                                onClick = {
                                    error = null
                                    entry = DoseEntry(medId, at, DoseStatus.TAKEN, null, revision, final)
                                }) { Text("Mark as taken") }
                            TextButton(enabled = !busy, onClick = {
                                error = null
                                entry = DoseEntry(medId, at, DoseStatus.SKIPPED, null, revision, final)
                            }) { Text("Skip dose") }
                        }
                        if (final) TextButton(enabled = !busy, onClick = {
                            error = null
                            entry = DoseEntry(medId, at, if (status == DoseStatus.MISSED) DoseStatus.TAKEN else status!!,
                                row?.dose?.recordedAt, revision, true)
                        }) { Text("Correct entry") }
                        row?.dose?.doseId?.let { doseId ->
                            TextButton(onClick = { showHistory(doseId) }) { Text("Entry history") }
                        }
                    }
                }
            }
        } else if (tab == 1) {
            PhmsSectionHeader("Your medicines", supportingText = "Inactive medicines stay here with their intake history.")
            if (all.isEmpty()) PhmsEmptyStateCard("Add your first medication", "Add your prescription and intake schedule.",
                PhmsIcons.Medication)
            all.forEach { med ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PhmsIconBadge(PhmsIcons.Medication)
                        Text("${med.name} · ${med.dosage}", style = MaterialTheme.typography.titleMedium)
                        Text(if (med.isActive) med.frequency else "Inactive")
                        Text(MedicationTimes.forEditing(med).joinToString(" · ", transform = DoseTimes::displayClock))
                        if (med.instructions.isNotBlank()) Text(med.instructions)
                        MedicationPrescriptionDetails(med, providers, pharmacies)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (med.isActive) TextButton(onClick = { onEdit(med) }) { Text("Edit") }
                            TextButton(onClick = { onAudit(med) }) { Text("Changes") }
                            if (med.isActive) TextButton(onClick = { onDeactivate(med) }) { Text("Deactivate") }
                        }
                    }
                }
            }
        } else {
            com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsDateInput(historyDay, { value ->
                value?.let { historyDay = it; customHistoryDay = true }
            }, "History date")
            PhmsSectionHeader("Medication history", supportingText = "Corrections keep the original entries in your history.")
            if (doses.isEmpty()) PhmsEmptyStateCard("No recorded doses on this date", "Choose another date or view today's schedule.",
                PhmsIcons.Continue, actionLabel = "Today's schedule", onAction = { tab = 0 })
            doses.forEach { row ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${row.medicationName} · ${row.dosage}", style = MaterialTheme.typography.titleMedium)
                        Text("Scheduled ${DoseTimes.formatTime(row.dose.scheduledAt)}")
                        if (row.dose.status == DoseStatus.TAKEN) TakenIntakeStatus(row.dose.recordedAt)
                        else {
                            Text(medicationIntakeState(row.dose.status, row.dose.scheduledAt, now,
                                all.any { it.medicationId == row.dose.medicationId && it.isActive }).label)
                            row.dose.recordedAt?.let { Text("Recorded ${intakeDateTime(it)}") }
                        }
                        Row {
                            TextButton(enabled = !busy, onClick = {
                                error = null
                                entry = DoseEntry(row.dose.medicationId, row.dose.scheduledAt,
                                    if (row.dose.status in listOf(DoseStatus.MISSED, DoseStatus.SCHEDULED)) DoseStatus.TAKEN else row.dose.status,
                                    row.dose.recordedAt, latestCorrections[row.dose.doseId]?.correctionId ?: 0L, true)
                            }) { Text("Correct entry") }
                            TextButton(onClick = { showHistory(row.dose.doseId) }) { Text("Entry history") }
                        }
                    }
                }
            }
        }
    }
    entry?.let { request ->
        DoseEntryDialog(request, busy, error, onDismiss = { entry = null; error = null }) { status, at, reason ->
            if (!busy) {
                busy = true; error = null
                scope.launch {
                    try {
                        val id = app.monitoringRepository.recordIntake(userId, request.medicationId, request.scheduledAt,
                            status, at, reason, request.revision)
                        if (status == DoseStatus.SCHEDULED)
                            DoseAlarms.scheduleMissedCheck(context, id, request.scheduledAt + MonitoringRepository.DEFAULT_MISSED_WINDOW_MS, userId)
                        else {
                            DoseAlarms.cancelMissedCheck(context, id)
                            DoseAlarms.cancelDoseNotification(context, id)
                        }
                        entry = null
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message ?: "Couldn't save intake." }
                    finally { busy = false; now = System.currentTimeMillis() }
                }
            }
        }
    }
    entryHistory?.let { history ->
        AlertDialog(onDismissRequest = { entryHistory = null }, title = { Text("Intake entry history") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    originalEntry?.let {
                        Text("Original entry: ${it.status}")
                        it.recordedAt?.let { at -> Text(intakeDateTime(at)) }
                    }
                    history.forEach {
                        Text(when (it.status) { DoseStatus.TAKEN -> "Taken"; DoseStatus.SKIPPED -> "Skipped"; DoseStatus.MISSED -> "Missed"; else -> "Not recorded" },
                            style = MaterialTheme.typography.titleSmall)
                        it.actualTakenAt?.let { at -> Text("Actual intake: ${intakeDateTime(at)}") }
                        Text("Entry recorded: ${intakeDateTime(it.changedAt)}")
                        Text(it.reason)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { entryHistory = null }) { Text("Close") } })
    }
}
