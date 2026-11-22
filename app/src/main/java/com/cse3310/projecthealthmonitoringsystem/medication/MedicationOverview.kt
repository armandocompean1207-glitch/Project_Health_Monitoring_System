package com.cse3310.projecthealthmonitoringsystem.medication

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as OutlinedCard
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card

import android.app.DatePickerDialog
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val all by remember(userId) { app.medicationRepository.observeAllMedications(userId) }.collectAsState(emptyList())
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var historyDay by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var customHistoryDay by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
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
                        if (state == MedicationIntakeState.TAKEN) {
                            TakenIntakeStatus(row?.dose?.recordedAt)
                        } else if (state == MedicationIntakeState.DUE) {
                            Button(enabled = !busy, colors = ButtonDefaults.buttonColors(containerColor = MedicationYellow, contentColor = MedicationInk),
                                onClick = {
                                    if (busy) return@Button
                                    busy = true
                                    error = null
                                    scope.launch {
                                        try {
                                            val confirmation = app.monitoringRepository.confirmMedicationIntake(userId, medId, at)
                                            confirmation.alert?.let { AlertHandoff.send(context, it) }
                                            when (confirmation.outcome) {
                                                MonitoringRepository.IntakeOutcome.TAKEN,
                                                MonitoringRepository.IntakeOutcome.ALREADY_TAKEN -> confirmation.doseId?.let { doseId ->
                                                    DoseAlarms.cancelMissedCheck(context, doseId)
                                                    DoseAlarms.cancelDoseNotification(context, doseId)
                                                }
                                                MonitoringRepository.IntakeOutcome.TOO_EARLY -> error = "This dose is not due yet."
                                                MonitoringRepository.IntakeOutcome.MISSED -> error = "This dose is marked missed. Its saved status has been kept."
                                                MonitoringRepository.IntakeOutcome.NOT_RECORDED -> error = "The intake window has passed without a recorded dose."
                                                MonitoringRepository.IntakeOutcome.UNAVAILABLE -> error = "This dose is no longer available."
                                            }
                                        } catch (e: CancellationException) { throw e
                                        } catch (e: Exception) { error = e.message ?: "Couldn't record intake."
                                        } finally { now = System.currentTimeMillis(); busy = false }
                                    }
                                }) { Text("Mark as taken") }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (med.isActive) TextButton(onClick = { onEdit(med) }) { Text("Edit") }
                            TextButton(onClick = { onAudit(med) }) { Text("Changes") }
                            if (med.isActive) TextButton(onClick = { onDeactivate(med) }) { Text("Deactivate") }
                        }
                    }
                }
            }
        } else {
            TextButton(onClick = {
                val cal = Calendar.getInstance().apply { timeInMillis = historyDay }
                DatePickerDialog(context, { _, y, m, d ->
                    historyDay = Calendar.getInstance().apply { set(y, m, d) }.timeInMillis
                    customHistoryDay = true
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }) { Text(DoseTimes.formatTime(range.first) + " · Change date") }
            PhmsSectionHeader("Intake history", supportingText = "Final taken and missed records cannot be edited or deleted.")
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
                    }
                }
            }
        }
    }
}
