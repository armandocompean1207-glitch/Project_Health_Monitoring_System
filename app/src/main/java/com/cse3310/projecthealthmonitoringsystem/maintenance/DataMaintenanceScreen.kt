package com.cse3310.projecthealthmonitoringsystem.maintenance

import android.widget.Toast
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.Doctor
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.data.entity.Specialty
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Section(val label: String, val one: String) {
    DOCTORS("Doctors", "doctor"),
    VISITS("Visits", "visit"),
    SPECIALTIES("Specialties", "specialty"),
    VITALS("Vital ranges", "vital type"),
    CONFLICTS("Conflict pairs", "conflict pair")
}

private val visitFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }

private fun parseVisitTime(text: String): Long? =
    try {
        visitFormat.parse(text.trim())?.time
    } catch (e: ParseException) {
        null
    }

/** What the delete dialog is about to remove. */
private data class PendingDelete(val title: String, val action: suspend () -> Boolean)

@Composable
fun DataMaintenanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.phms()
    val repo = app.maintenanceRepository
    val userId = CurrentUser.userId
    val scope = rememberCoroutineScope()

    val doctors by remember { repo.observeDoctors(userId) }.collectAsState(initial = emptyList())
    val visits by remember { repo.observeDoctorVisits(userId) }.collectAsState(initial = emptyList())
    val specialties by remember { repo.observeSpecialties() }.collectAsState(initial = emptyList())
    val vitalTypes by remember { repo.observeVitalTypes() }.collectAsState(initial = emptyList())
    val rules by remember { repo.observeConflictRules() }.collectAsState(initial = emptyList())

    var section by remember { mutableStateOf(Section.DOCTORS) }
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

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun open(id: Long = 0L, a: String = "", b: String = "", c: String = "", d: String = "", e: String = "", f: String = "", pick: Long? = null) {
        editId = id; f1 = a; f2 = b; f3 = c; f4 = d; f5 = e; f6 = f; pickedId = pick
        formError = null
        formOpen = true
    }

    fun done(ok: Boolean, failMessage: String) {
        if (ok) {
            toast("Saved")
            formOpen = false
        } else {
            formError = failMessage
        }
    }

    fun save() {
        formError = null
        scope.launch {
            when (section) {
                Section.DOCTORS -> {
                    if (f1.isBlank()) { formError = "Enter the doctor's name."; return@launch }
                    val doctor = Doctor(editId, userId, f1.trim(), pickedId, f2.trim(), f3.trim(), f4.trim())
                    val ok = if (editId == 0L) repo.addDoctor(userId, doctor) > 0 else repo.editDoctor(userId, doctor)
                    done(ok, "Could not save the doctor.")
                }
                Section.VISITS -> {
                    val doctorId = pickedId ?: run { formError = "Pick a doctor."; return@launch }
                    val at = parseVisitTime(f1) ?: run { formError = "Enter the date as yyyy-MM-dd HH:mm, for example 2026-12-03 14:30."; return@launch }
                    val visit = DoctorVisit(editId, userId, doctorId, at, f2.trim(), f3.trim())
                    val ok = if (editId == 0L) repo.addDoctorVisit(userId, visit) != null else repo.editDoctorVisit(userId, visit)
                    done(ok, "Could not save the visit.")
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
                    val severity = f3.ifBlank { "MEDIUM" }
                    val ok = if (editId == 0L) repo.addConflictRule(a, b, severity, f4.trim()) != null
                    else repo.editConflictRule(editId, a, b, severity, f4.trim())
                    done(ok, "That pair is already in the table.")
                }
            }
        }
    }

    fun LazyListScope.row(title: String, lines: List<String>, onEdit: () -> Unit, onDelete: () -> Unit) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    lines.filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Row {
                        TextButton(onClick = onEdit) { Text("Edit") }
                        TextButton(onClick = onDelete) { Text("Delete") }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Back to More") }
            Text("Data Maintenance", style = MaterialTheme.typography.headlineMedium)
            Text("Doctors, visits and the lookup tables the app uses.", style = MaterialTheme.typography.bodyMedium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Section.values().forEach { s ->
                    FilterChip(
                        selected = section == s,
                        onClick = { section = s; formOpen = false },
                        label = { Text(s.label) }
                    )
                }
            }
        }

        if (!formOpen) {
            item {
                Button(onClick = { open() }) {
                    Text("Add ${section.one}")
                }
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
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
                                Field(f1, { f1 = it }, "Date and time (yyyy-MM-dd HH:mm)")
                                Field(f2, { f2 = it }, "Reason (e.g. Annual checkup)")
                                Field(f3, { f3 = it }, "Notes")
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
                            Button(onClick = { save() }) { Text("Save") }
                            OutlinedButton(onClick = { formOpen = false }) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        when (section) {
            Section.DOCTORS -> {
                if (doctors.isEmpty()) item { Text("No doctors yet.") }
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
                if (visits.isEmpty()) item { Text("No visits yet.") }
                visits.forEach { v ->
                    row(
                        "${v.doctorName} · ${DoseTimes.formatTime(v.visit.visitAt)}",
                        listOf(v.visit.reason, v.visit.notes),
                        onEdit = { open(v.visit.visitId, visitFormat.format(Date(v.visit.visitAt)), v.visit.reason, v.visit.notes, pick = v.visit.doctorId) },
                        onDelete = {
                            pendingDelete = PendingDelete("Delete this visit with ${v.doctorName}?") { repo.deleteDoctorVisit(userId, v.visit.visitId) }
                        }
                    )
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
                Text("These values are sample data for the class project and are not medical advice.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete?") },
            text = { Text(p.title) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { toast(if (p.action()) "Deleted" else "Could not delete, it is still in use.") }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
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
