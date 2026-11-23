package com.cse3310.projecthealthmonitoringsystem.medication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import java.util.Calendar

internal data class DoseEntry(val medicationId: Long, val scheduledAt: Long, val initial: DoseStatus,
    val actualTakenAt: Long?, val revision: Long, val correction: Boolean)

@Composable
internal fun DoseEntryDialog(entry: DoseEntry, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onSave: (DoseStatus, Long?, String) -> Unit) {
    val context = LocalContext.current
    var status by remember(entry) { mutableStateOf(entry.initial) }
    var actualAt by remember(entry) { mutableLongStateOf(entry.actualTakenAt ?: System.currentTimeMillis()) }
    var actualDateValid by remember(entry) { mutableStateOf(true) }
    var reason by remember(entry) { mutableStateOf(if (entry.correction) "Corrected an accidental entry" else "Patient recorded intake") }
    val dismiss = rememberGuardedDismiss(true, busy, onDismiss)
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (entry.correction) "Correct intake entry" else "Record dose") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Scheduled ${intakeDateTime(entry.scheduledAt)}")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DoseStatus.TAKEN, DoseStatus.SKIPPED).let {
                        if (entry.correction) it + DoseStatus.SCHEDULED else it
                    }.forEach { option ->
                        FilterChip(selected = status == option, enabled = !busy, onClick = { status = option },
                            label = { Text(when (option) { DoseStatus.TAKEN -> "Taken"; DoseStatus.SKIPPED -> "Skipped"; else -> "Not recorded" }) })
                    }
                }
                if (status == DoseStatus.TAKEN) {
                    Text("Confirm only a dose you actually took. Record the actual time.")
                    PhmsDateInput(actualAt, { it?.let { value -> actualAt = value } }, "Actual intake date",
                        maxDate = System.currentTimeMillis(), enabled = !busy, onValidityChange = { actualDateValid = it })
                    PhmsOutlinedButton(enabled = !busy && actualDateValid, onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = actualAt }
                        TimePickerDialog(context, { _, hour, minute ->
                            actualAt = Calendar.getInstance().apply {
                                timeInMillis = actualAt; set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
                    }) { Text("Actual intake time · ${intakeDateTime(actualAt).substringAfter("·").trim()}") }
                }
                if (status == DoseStatus.SKIPPED) Text("This records your decision to skip this dose; it is not medication advice.")
                if (entry.correction) PhmsTextField(reason, { reason = it }, enabled = !busy,
                    label = { Text("Reason for correction") }, modifier = Modifier.fillMaxWidth())
                Text("Original entries and corrections remain in history.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { PhmsTextButton(enabled = !busy && (status != DoseStatus.TAKEN || actualDateValid), onClick = {
            onSave(status, if (status == DoseStatus.TAKEN) actualAt else null,
                if (entry.correction) reason else if (status == DoseStatus.SKIPPED) "Patient recorded a skipped dose" else "Patient recorded intake")
        }) { Text(if (busy) "Saving…" else "Save entry") } },
        dismissButton = { PhmsTextButton(enabled = !busy, onClick = dismiss) { Text("Cancel") } })
}
