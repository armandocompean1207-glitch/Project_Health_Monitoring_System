package com.cse3310.projecthealthmonitoringsystem.healthguide

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination
import com.cse3310.projecthealthmonitoringsystem.data.repository.VaccinationDates
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
internal fun VaccinationsSection(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val repo = context.phms().vaccinationRepository
    val owner = CurrentUser.userId
    val records by remember(owner) { repo.observe(owner) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember(owner) { mutableStateOf<Vaccination?>(null) }
    var formOpen by remember(owner) { mutableStateOf(false) }
    var removing by remember(owner) { mutableStateOf<Vaccination?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Color(0xFF065F46),
        onPrimary = Color.White, primaryContainer = Color(0xFF065F46), onPrimaryContainer = Color.White)) {
        if (onBack != null) PhmsScreenHeader("Vaccinations", icon = Icons.Outlined.Vaccines,
            onBack = { if (!formOpen) onBack() }) else PhmsSectionHeader("Vaccinations")
        if (!formOpen) Button(onClick = { editing = null; error = null; formOpen = true }) {
            Text("Add Vaccination")
        }
        if (formOpen) key(editing?.vaccinationId) {
            VaccinationEditor(editing, owner, busy, error, onDismiss = { formOpen = false }, onSave = { value ->
                if (!busy) {
                    busy = true; error = null
                    scope.launch {
                        try {
                            val id = repo.save(owner, value)
                            val saved = checkNotNull(repo.get(owner, id))

                            // The record remains saved even if Android cannot schedule its optional reminder.
                            val scheduled = runCatching { VaccinationReminders.schedule(context, saved) }.isSuccess
                            formOpen = false
                            snackbar.showSnackbar(if (scheduled) "Vaccination saved." else "Vaccination saved. Check reminder settings.")
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (failure: Exception) { error = failure.message ?: "Couldn't save this vaccination."
                        } finally { busy = false }
                    }
                }
            })
        }
        error?.takeIf { !formOpen }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (records.isEmpty()) Text("Keep a record of vaccinations you've received. Add only dates and next doses provided by your provider.")
        records.forEach { record ->
            var expanded by remember(record.vaccinationId) { mutableStateOf(false) }
            OutlinedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE8E1D8))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PhmsIconBadge(Icons.Outlined.Vaccines)
                        Column(Modifier.weight(1f)) {
                            Text(record.name, style = MaterialTheme.typography.titleMedium)
                            Text("Received ${VaccinationDates.label(record.receivedDate)}")
                            record.doseNumber?.let { Text("Dose $it") }
                        }
                        Icon(PhmsIcons.Next, if (expanded) "Hide details" else "View details")
                    }
                    record.nextDoseDate?.let { Text("Next dose: ${VaccinationDates.label(it)}") }
                    if (expanded) {
                        if (record.providerOrPharmacy.isNotBlank()) Text(record.providerOrPharmacy)
                        if (record.location.isNotBlank()) Text(record.location)
                        if (record.notes.isNotBlank()) Text(record.notes)
                        if (record.reminderEnabled) VaccinationReminders.reminderMinutes(context, record)?.let { minutes ->
                            Text("Reminder: ${com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes.displayClock(minutes)} on your next dose date.")
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PhmsTextButton(enabled = !busy, onClick = { editing = record; error = null; formOpen = true }) { Text("Edit") }
                            PhmsTextButton(enabled = !busy, onClick = { removing = record }) { Text("Delete") }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar)

        removing?.let { record ->
            AlertDialog(onDismissRequest = { if (!busy) removing = null }, title = { Text("Delete vaccination?") },
                text = { Text("Remove ${record.name} and its reminder? This cannot be undone.") },
                confirmButton = { PhmsTextButton(enabled = !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        try {
                            check(repo.delete(owner, record.vaccinationId)) { "This record is no longer available." }
                            VaccinationReminders.cancel(context, owner, record.vaccinationId)
                            removing = null
                            snackbar.showSnackbar("Vaccination deleted.")
                        } catch (cancelled: CancellationException) { throw cancelled
                        } catch (failure: Exception) { error = failure.message ?: "Couldn't delete this vaccination."; removing = null
                        } finally { busy = false }
                    }
                }) { Text("Delete") } },
                dismissButton = { PhmsTextButton(enabled = !busy, onClick = { removing = null }) { Text("Cancel") } })
        }
    }
}

@Composable
private fun VaccinationEditor(original: Vaccination?, owner: String, busy: Boolean, saveError: String?,
    onDismiss: () -> Unit, onSave: (Vaccination) -> Unit) {
    var name by remember { mutableStateOf(original?.name.orEmpty()) }
    var received by remember { mutableStateOf(original?.receivedDate ?: VaccinationDates.iso(System.currentTimeMillis())) }
    var dose by remember { mutableStateOf(original?.doseNumber?.toString().orEmpty()) }
    var provider by remember { mutableStateOf(original?.providerOrPharmacy.orEmpty()) }
    var location by remember { mutableStateOf(original?.location.orEmpty()) }
    var next by remember { mutableStateOf(original?.nextDoseDate) }
    var notes by remember { mutableStateOf(original?.notes.orEmpty()) }
    var remind by remember { mutableStateOf(original?.reminderEnabled ?: false) }
    var choosingReminderDate by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var receivedValid by remember { mutableStateOf(true) }
    var nextValid by remember { mutableStateOf(true) }
    val value = Vaccination(original?.vaccinationId ?: 0, owner, name, received, dose.toIntOrNull(),
        provider, location, next, notes, remind, original?.reminderNotifiedFor)
    val issue = if (remind && next == null) "Choose a next scheduled dose date."
        else if (!receivedValid || !nextValid) "Enter valid dates as MM/DD/YYYY." else if (dose.isNotBlank() && dose.toIntOrNull() == null) "Enter a positive whole dose number."
        else VaccinationDates.issue(value, System.currentTimeMillis(), original)
    val dirty = value != (original ?: Vaccination(userId = owner, name = "", receivedDate = VaccinationDates.iso(System.currentTimeMillis())))
    val dismiss = rememberGuardedDismiss(dirty, busy, onDismiss)
    androidx.activity.compose.BackHandler { dismiss() }
    PhmsCard(Modifier.fillMaxWidth().imePadding(),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PhmsScreenHeader(if (original == null) "Add Vaccination" else "Edit vaccination",
                icon = Icons.Outlined.Vaccines, onBack = dismiss)
                PhmsTextField(name, { name = it }, label = { Text("Vaccine name") }, enabled = !busy,
                    isError = attempted && name.isBlank(), modifier = Modifier.fillMaxWidth())
                PhmsDateInput(VaccinationDates.parse(received), { it?.let { time -> received = VaccinationDates.iso(time) } },
                    "Date received", maxDate = System.currentTimeMillis(), enabled = !busy, onValidityChange = { receivedValid = it })
                PhmsTextField(dose, { dose = it }, label = { Text("Dose number (optional)") }, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = attempted && dose.isNotBlank() && (dose.toIntOrNull() ?: 0) <= 0)
                PhmsTextField(provider, { provider = it }, label = { Text("Provider or pharmacy (optional)") }, enabled = !busy)
                PhmsTextField(location, { location = it }, label = { Text("Location (optional)") }, enabled = !busy)
                PhmsDateInput(next?.let(VaccinationDates::parse), { next = it?.let(VaccinationDates::iso); if (it == null) remind = false },
                    "Next scheduled dose date (optional)", required = false, minDate = VaccinationDates.parse(received), enabled = !busy,
                    onValidityChange = { nextValid = it })
                if (next != null) PhmsTextButton(onClick = { next = null; remind = false }, enabled = !busy) { Text("Remove next dose date") }
                PhmsTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, enabled = !busy)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = remind, enabled = !busy, onCheckedChange = { checked ->
                        if (checked && next == null) choosingReminderDate = true else remind = checked
                    })
                    Text("Remind me on the next dose date.", Modifier.weight(1f))
                }

                if (remind) Text("Notifications must be enabled in Settings. Android may delay reminders.", style = MaterialTheme.typography.bodySmall)
                if (attempted && issue != null) Text(issue, color = MaterialTheme.colorScheme.error)
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { attempted = true; if (issue == null) onSave(value) }) {
                    Text(if (busy) "Saving…" else "Save vaccination")
                }
                PhmsOutlinedButton(enabled = !busy, onClick = dismiss) { Text("Cancel") }
            }
        }
    }
    if (choosingReminderDate) PhmsCalendarPicker(
        initial = next?.let(VaccinationDates::parse) ?: System.currentTimeMillis(),
        minDate = VaccinationDates.parse(received), maxDate = null,
        onDismiss = { choosingReminderDate = false },
        onConfirm = { date ->
            next = VaccinationDates.iso(date)
            nextValid = true
            remind = true
            choosingReminderDate = false
        })

}
