package com.cse3310.projecthealthmonitoringsystem.maintenance

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.rememberLazyListState
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as OutlinedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.ui.Alignment
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.Doctor
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.data.entity.Specialty
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import kotlinx.coroutines.launch

private enum class Section(val label: String, val one: String) {
    DOCTORS("Doctors", "doctor"),
    VISITS("Appointments", "appointment"),
    CHECKUPS("Annual checkups", "checkup date"),
    SPECIALTIES("Specialties", "specialty"),
    VITALS("Vital ranges", "vital type"),
    CONFLICTS("Conflict pairs", "conflict pair")
}

/** What the delete dialog is about to remove. */
private data class PendingDelete(val title: String, val action: suspend () -> Boolean)

@Composable
fun DataMaintenanceScreen(onBack: () -> Unit, onOpenMedication: () -> Unit = {}, initialAppointments: Boolean = false, initialCheckups: Boolean = false) {
    val context = LocalContext.current
    val app = context.phms()
    val repo = app.maintenanceRepository
    val userId = CurrentUser.userId
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    if (userId.isBlank()) return
    val profile by remember(userId) { app.authRepository.observeProfile(userId) }.collectAsState(initial = null)
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var visitFilter by rememberSaveable { mutableStateOf("Upcoming") }
    var annualVisit by remember { mutableStateOf(false) }

    val doctors by remember(userId) { repo.observeDoctors(userId) }.collectAsState(initial = emptyList())
    val visits by remember(userId) { repo.observeDoctorVisits(userId) }.collectAsState(initial = emptyList())
    val specialties by remember { repo.observeSpecialties() }.collectAsState(initial = emptyList())
    val vitalTypes by remember { repo.observeVitalTypes() }.collectAsState(initial = emptyList())
    val rules by remember { repo.observeConflictRules() }.collectAsState(initial = emptyList())

    var section by rememberSaveable(userId) { mutableStateOf(if (initialCheckups) Section.CHECKUPS else if (initialAppointments) Section.VISITS else Section.DOCTORS) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var formOpen by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    // One set of form fields reused by every section; f1..f6 mean different things per section.
    var editId by remember { mutableStateOf(0L) }
    var f1 by remember { mutableStateOf("") }
    var f2 by remember { mutableStateOf("") }
    var f3 by remember { mutableStateOf("") }
    var f4 by remember { mutableStateOf("") }
    var f5 by remember { mutableStateOf("") }
    var f6 by remember { mutableStateOf("") }
    var pickedId by remember { mutableStateOf<Long?>(null) }

    fun open(id: Long = 0L, a: String = "", b: String = "", c: String = "", d: String = "", e: String = "", f: String = "", pick: Long? = null) {
        editId = id; f1 = a; f2 = b; f3 = c; f4 = d; f5 = e; f6 = f; pickedId = pick
        formError = null
        annualVisit = section == Section.VISITS && isAnnualCheckup(b)
        formOpen = true
        scope.launch { listState.animateScrollToItem(1) }
    }

    fun done(ok: Boolean, failMessage: String) {
        if (ok) {
            message = "Saved successfully."
            formOpen = false
        } else {
            formError = failMessage
        }
    }

    fun save() {
        if (busy) return
        formError = null
        busy = true
        scope.launch {
            try {
            when (section) {
                Section.DOCTORS -> {
                    if (f1.isBlank()) { formError = "Enter the doctor's name."; return@launch }
                    doctorContactError(f2, f3)?.let { formError = it; return@launch }
                    val doctor = Doctor(editId, userId, f1.trim(), pickedId, f2.trim(), f3.trim(), f4.trim())
                    val ok = if (editId == 0L) repo.addDoctor(userId, doctor) > 0 else repo.editDoctor(userId, doctor)
                    done(ok, "Could not save the doctor.")
                }
                Section.VISITS -> {
                    val doctorId = pickedId ?: run { formError = "Pick a doctor."; return@launch }
                    val at = parseVisitTime(f1) ?: run { formError = "Choose an appointment date and time."; return@launch }
                    val reason = if (annualVisit && !isAnnualCheckup(f2)) "Annual checkup" + f2.trim().takeIf { it.isNotEmpty() }?.let { " · $it" }.orEmpty() else f2.trim()
                    val visit = DoctorVisit(editId, userId, doctorId, at, reason, f3.trim())
                    val ok = if (editId == 0L) repo.addDoctorVisit(userId, visit) != null else repo.editDoctorVisit(userId, visit)
                    done(ok, "Could not save the visit.")
                }
                Section.CHECKUPS -> {
                    val at = parseVisitTime(f1) ?: run { formError = "Choose a checkup date and time."; return@launch }
                    val latest = app.authRepository.getProfile(userId)
                        ?: run { formError = "Your profile could not be loaded."; return@launch }
                    done(app.authRepository.updateProfile(userId, latest.copy(annualCheckupAt = at)), "Could not save the checkup date.")
                }
                Section.SPECIALTIES -> {
                    if (f1.isBlank()) { formError = "Enter a specialty name."; return@launch }
                    val ok = if (editId == 0L) repo.addSpecialty(f1) != null else repo.editSpecialty(Specialty(editId, f1))
                    done(ok, "That specialty already exists.")
                }
                Section.VITALS -> {
                    val min = f3.toDoubleOrNull()
                    val max = f4.toDoubleOrNull()
                    val min2 = f5.toDoubleOrNull()
                    val max2 = f6.toDoubleOrNull()
                    when {
                        f1.isBlank() || f2.isBlank() -> { formError = "Enter a name and a unit."; return@launch }
                        min == null || max == null -> { formError = "Enter the normal minimum and maximum as numbers."; return@launch }
                        min > max -> { formError = "The minimum must not be more than the maximum."; return@launch }
                        (f5.isNotBlank() && min2 == null) || (f6.isNotBlank() && max2 == null) -> {
                            formError = "Second reading range must be numbers."; return@launch
                        }
                    }
                    rangeError(min, max, f5, f6)?.let { formError = it; return@launch }
                    val hasSecond = min2 != null || max2 != null
                    val type = VitalType(
                        vitalTypeId = editId,
                        name = f1,
                        unit = f2.trim(),
                        normalMin = min!!,
                        normalMax = max!!,
                        secondaryLabel = if (hasSecond) vitalTypes.firstOrNull { it.vitalTypeId == editId }?.secondaryLabel ?: "Second reading" else null,
                        secondaryMin = min2,
                        secondaryMax = max2
                    )
                    val ok = if (editId == 0L) repo.addVitalType(type) != null else repo.editVitalType(type)
                    done(ok, "That vital type already exists.")
                }
                Section.CONFLICTS -> {
                    val a = f1.trim()
                    val b = f2.trim()
                    if (a.isEmpty() || b.isEmpty() || a.equals(b, ignoreCase = true)) {
                        formError = "Enter two different drug names."; return@launch
                    }
                    if (f4.isBlank()) { formError = "Describe the interaction rule."; return@launch }
                    val severity = f3.ifBlank { "MEDIUM" }
                    val ok = if (editId == 0L) repo.addConflictRule(a, b, severity, f4.trim()) != null
                    else repo.editConflictRule(editId, a, b, severity, f4.trim())
                    done(ok, "That pair is already in the table.")
                }
            }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { formError = "Couldn't save. Check the fields and try again."
            } finally { busy = false }
        }
    }
    val dismissEditor = rememberGuardedDismiss(formOpen, busy) { formOpen = false }

    fun LazyListScope.row(title: String, lines: List<String>, onEdit: () -> Unit, onDelete: () -> Unit) {
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhmsIconBadge(when (section) {
                            Section.DOCTORS -> PhmsIcons.Profile
                            Section.VISITS, Section.CHECKUPS -> PhmsIcons.Appointments
                            Section.VITALS -> PhmsIcons.Vitals
                            else -> PhmsIcons.DataMaintenance
                        })
                        Text(title, style = MaterialTheme.typography.titleMedium)
                    }
                    lines.filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Row {
                        TextButton(enabled = !busy, onClick = onEdit) { Text("Edit") }
                        TextButton(enabled = !busy, onClick = onDelete) { Text("Delete") }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PhmsScreenHeader(if (initialAppointments) "Appointments" else "Care details", "Your care team and health information.", PhmsIcons.Appointments,
                onBack = { if (formOpen) dismissEditor() else if (!busy) onBack() })
            OutlinedButton(enabled = !busy, onClick = onOpenMedication, modifier = Modifier.padding(top = 12.dp)) {
                Icon(PhmsIcons.Medication, null)
                Text("Manage medications", Modifier.padding(start = 8.dp))
            }
            if (section in listOf(Section.SPECIALTIES, Section.VITALS, Section.CONFLICTS)) {
                Text("Shared reference options apply to every account on this device.", style = MaterialTheme.typography.bodySmall)
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 8.dp)) }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Section.values().forEach { s ->
                    FilterChip(
                        selected = section == s,
                        enabled = !busy,
                        onClick = { if (formOpen) dismissEditor() else { section = s; formError = null; message = null } },
                        label = { Text(s.label) }
                    )
                }
            }
        }

        if (!formOpen && section != Section.CHECKUPS) {
            item {
                Button(enabled = !busy, onClick = {
                    if (section == Section.VISITS && doctors.isEmpty()) section = Section.DOCTORS
                    open()
                }) {
                    Text(if (section == Section.VISITS && doctors.isEmpty()) "Add doctor" else "Add ${section.one}")
                }
            }
        } else if (formOpen) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text((if (editId == 0L) "Add " else "Edit ") + section.one, style = MaterialTheme.typography.titleMedium)
                        when (section) {
                            Section.DOCTORS -> {
                                Field(f1, { f1 = it }, "Name")
                                Text("Specialty")
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = pickedId == null, onClick = { pickedId = null }, label = { Text("None") })
                                    specialties.forEach { s ->
                                        FilterChip(selected = pickedId == s.specialtyId, onClick = { pickedId = s.specialtyId }, label = { Text(s.name) })
                                    }
                                }
                                Field(f2, { f2 = it }, "Phone", KeyboardType.Phone)
                                Field(f3, { f3 = it }, "Email", KeyboardType.Email)
                                Field(f4, { f4 = it }, "Address")
                            }
                            Section.VISITS -> {
                                Text("Doctor")
                                if (doctors.isEmpty()) Text("Add a doctor first.", color = MaterialTheme.colorScheme.error)
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    doctors.forEach { d ->
                                        FilterChip(selected = pickedId == d.doctor.doctorId, onClick = { pickedId = d.doctor.doctorId }, label = { Text(d.doctor.name) })
                                    }
                                }
                                VisitTimeField(f1) { f1 = it }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = annualVisit, onCheckedChange = {
                                        annualVisit = it
                                        if (!it && isAnnualCheckup(f2)) f2 = ""
                                    })
                                    Text("Annual checkup", style = MaterialTheme.typography.bodyLarge)
                                }
                                Field(f2, { f2 = it }, "Reason (e.g. Annual checkup)")
                                Field(f3, { f3 = it }, "Notes")
                            }
                            Section.CHECKUPS -> {
                                Text("Next due date is stored in your profile. Record completed checkups as appointments.", style = MaterialTheme.typography.bodyMedium)
                                VisitTimeField(f1) { f1 = it }
                            }
                            Section.SPECIALTIES -> Field(f1, { f1 = it }, "Specialty name")
                            Section.VITALS -> {
                                Field(f1, { f1 = it }, "Name (e.g. Heart Rate)")
                                Field(f2, { f2 = it }, "Unit (e.g. bpm)")
                                Field(f3, { f3 = it }, "Normal minimum", KeyboardType.Decimal)
                                Field(f4, { f4 = it }, "Normal maximum", KeyboardType.Decimal)
                                Text("Second reading, only for two-number vitals like blood pressure", style = MaterialTheme.typography.bodySmall)
                                Field(f5, { f5 = it }, "Second minimum", KeyboardType.Decimal)
                                Field(f6, { f6 = it }, "Second maximum", KeyboardType.Decimal)
                            }
                            Section.CONFLICTS -> {
                                Field(f1, { f1 = it }, "First drug")
                                Field(f2, { f2 = it }, "Second drug")
                                Text("Severity")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("LOW", "MEDIUM", "HIGH").forEach { s ->
                                        FilterChip(selected = f3.ifBlank { "MEDIUM" } == s, onClick = { f3 = s }, label = { Text(s) })
                                    }
                                }
                                Field(f4, { f4 = it }, "What happens (e.g. Increased bleeding risk)")
                            }
                        }
                        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy, onClick = { save() }) { Text(if (busy) "Saving…" else "Save") }
                            OutlinedButton(enabled = !busy, onClick = dismissEditor) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        when (section) {
            Section.DOCTORS -> {
                if (doctors.isEmpty() && !formOpen) item {
                    PhmsEmptyStateCard("Build your care team", "Save a doctor before adding your first appointment.",
                        PhmsIcons.Profile)
                }
                doctors.forEach { d ->
                    row(
                        d.doctor.name,
                        listOf(d.specialtyName ?: "", d.doctor.phone, d.doctor.email, d.doctor.address),
                        onEdit = { open(d.doctor.doctorId, d.doctor.name, d.doctor.phone, d.doctor.email, d.doctor.address, pick = d.doctor.specialtyId) },
                        onDelete = {
                            pendingDelete = PendingDelete("Delete ${d.doctor.name}? Their visits are deleted too; medications keep their data.") {
                                repo.deleteDoctor(userId, d.doctor.doctorId)
                            }
                        }
                    )
                }
            }
            Section.VISITS -> {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Upcoming", "Past", "All").forEach { value ->
                            FilterChip(selected = visitFilter == value, onClick = { visitFilter = value }, label = { Text(value) })
                        }
                    }
                    Text("Saved locally; adding a visit does not book it with your doctor.", style = MaterialTheme.typography.bodySmall)
                }
                val now = System.currentTimeMillis()
                val filtered = visits.filter { visitFilter == "All" ||
                    if (visitFilter == "Past") it.visit.visitAt < now else it.visit.visitAt >= now }
                    .let { if (visitFilter == "Upcoming") it.sortedBy { v -> v.visit.visitAt } else it }
                if (filtered.isEmpty() && !formOpen) item {
                    PhmsEmptyStateCard("No ${visitFilter.lowercase()} appointments", "Record an appointment you've arranged with your care team.",
                        PhmsIcons.Appointments)
                }
                filtered.forEach { v ->
                    row(
                        v.doctorName,
                        listOf(visitDisplay(v.visit.visitAt), if (v.visit.visitAt < now) "Past appointment" else "Upcoming appointment", v.visit.reason, v.visit.notes),
                        onEdit = { open(v.visit.visitId, visitInput(v.visit.visitAt), v.visit.reason, v.visit.notes, pick = v.visit.doctorId) },
                        onDelete = {
                            pendingDelete = PendingDelete("Delete this visit with ${v.doctorName}?") { repo.deleteDoctorVisit(userId, v.visit.visitId) }
                        }
                    )
                }
            }
            Section.CHECKUPS -> {
                val current = profile
                item {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PhmsIconBadge(PhmsIcons.Appointments)
                            Text("Next annual checkup", style = MaterialTheme.typography.titleMedium)
                            Text(current?.annualCheckupAt?.let(::visitDisplay) ?: "No due date saved")
                            Text("A reminder date for your next yearly checkup.", style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(enabled = !busy && current != null, onClick = {
                                    open(a = current?.annualCheckupAt?.let(::visitInput).orEmpty())
                                }) { Text("Set due date") }
                                if (current?.annualCheckupAt != null) TextButton(enabled = !busy, onClick = {
                                    pendingDelete = PendingDelete("Clear the annual checkup due date? Recorded appointments remain saved.") {
                                        val latest = app.authRepository.getProfile(userId)
                                        latest != null && app.authRepository.updateProfile(userId, latest.copy(annualCheckupAt = null))
                                    }
                                }) { Text("Clear date") }
                            }
                        }
                    }
                }
                val checkups = visits.filter { isAnnualCheckup(it.visit.reason) }
                item { PhmsSectionHeader("Recorded checkups", supportingText = "Appointment dates and details you have saved.") }
                if (checkups.isEmpty()) item {
                    PhmsEmptyStateCard("No checkups recorded", "Mark an appointment as Annual checkup to keep its history here.",
                        PhmsIcons.Appointments, actionLabel = "Record checkup", onAction = {
                            section = if (doctors.isEmpty()) Section.DOCTORS else Section.VISITS
                            open()
                            annualVisit = doctors.isNotEmpty()
                        })
                }
                checkups.forEach { v ->
                    row(v.doctorName, listOf(visitDisplay(v.visit.visitAt), v.visit.reason, v.visit.notes),
                        onEdit = {
                            section = Section.VISITS
                            open(v.visit.visitId, visitInput(v.visit.visitAt), v.visit.reason, v.visit.notes, pick = v.visit.doctorId)
                        }, onDelete = {
                            pendingDelete = PendingDelete("Delete this recorded checkup? The profile's due date is kept separately.") {
                                repo.deleteDoctorVisit(userId, v.visit.visitId)
                            }
                        })
                }
            }
            Section.SPECIALTIES -> specialties.forEach { s ->
                row(
                    s.name, emptyList(),
                    onEdit = { open(s.specialtyId, s.name) },
                    onDelete = { pendingDelete = PendingDelete("Delete ${s.name}? Doctors with it keep their data.") { repo.deleteSpecialty(s.specialtyId) } }
                )
            }
            Section.VITALS -> vitalTypes.forEach { t ->
                row(
                    "${t.name} (${t.unit})",
                    listOf(
                        "Normal: ${num(t.normalMin)} – ${num(t.normalMax)}",
                        if (t.secondaryMin != null || t.secondaryMax != null)
                            "${t.secondaryLabel ?: "Second"}: ${t.secondaryMin?.let(::num) ?: "?"} – ${t.secondaryMax?.let(::num) ?: "?"}" else ""
                    ),
                    onEdit = {
                        open(t.vitalTypeId, t.name, t.unit, num(t.normalMin), num(t.normalMax),
                            t.secondaryMin?.let(::num) ?: "", t.secondaryMax?.let(::num) ?: "")
                    },
                    onDelete = {
                        pendingDelete = PendingDelete("Delete ${t.name}? This only works if no readings use it.") { repo.deleteVitalType(t.vitalTypeId) }
                    }
                )
            }
            Section.CONFLICTS -> rules.forEach { r ->
                row(
                    "${r.drugA} + ${r.drugB}",
                    listOf("Severity: ${r.severity}", r.description),
                    onEdit = { open(r.ruleId, r.drugA, r.drugB, r.severity, r.description) },
                    onDelete = { pendingDelete = PendingDelete("Delete ${r.drugA} + ${r.drugB}?") { repo.deleteConflictRule(r.ruleId) } }
                )
            }
        }
        item {
            if (section == Section.VITALS || section == Section.CONFLICTS) {
                Text("Shared reference data for all accounts on this device. Sample ranges and interaction rules are for the class project, not medical advice.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { if (!busy) pendingDelete = null },
            title = { Text("Confirm removal") },
            text = { Text(p.title) },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            try {
                                message = if (p.action()) "Removed successfully." else "Could not remove. This item may still be in use."
                            } catch (e: CancellationException) { throw e
                            } catch (_: Exception) { message = "Could not remove. Existing records were preserved."
                            } finally { busy = false; pendingDelete = null }
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, type: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun num(d: Double): String = if (d == Math.floor(d)) d.toLong().toString() else d.toString()
