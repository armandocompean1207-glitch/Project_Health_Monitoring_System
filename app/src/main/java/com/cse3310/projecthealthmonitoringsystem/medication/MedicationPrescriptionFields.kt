package com.cse3310.projecthealthmonitoringsystem.medication

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.dao.DoctorWithSpecialty
import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun MedicationPrescriptionFields(pharmacies: List<Pharmacy>, selectedId: Long?, rx: String,
    refills: String, nextDate: Long?, enabled: Boolean, onPharmacy: (Long?) -> Unit,
    onRx: (String) -> Unit, onRefills: (String) -> Unit, onDate: (Long?) -> Unit, onAddPharmacy: () -> Unit,
    onDateValidityChange: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf(false) }
    Text("Pharmacy (optional)")
    Box {
        PhmsOutlinedButton(enabled = enabled, onClick = { choosing = true }, modifier = Modifier.fillMaxWidth()) {
            Text(pharmacies.firstOrNull { it.pharmacyId == selectedId }?.let {
                it.name + if (it.isPreferred) " · Preferred" else ""
            } ?: "None", Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, "Choose pharmacy")
        }
        DropdownMenu(choosing, onDismissRequest = { choosing = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onPharmacy(null); choosing = false })
            pharmacies.forEach { p ->
                DropdownMenuItem(text = { Text(p.name + if (p.isPreferred) " · Preferred" else "") },
                    onClick = { onPharmacy(p.pharmacyId); choosing = false })
            }
            DropdownMenuItem(text = { Text("+ Add pharmacy") }, onClick = { choosing = false; onAddPharmacy() })
        }
    }
    PhmsTextField(rx, onRx, label = { Text("Prescription number (Rx #, optional)") },
        enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
    PhmsTextField(refills, onRefills, label = { Text("Refills remaining (optional)") },
        enabled = enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth())
    PhmsDateInput(nextDate, { date -> onDate(date?.let(MedicationTimes::dayStart)) }, "Next refill date (optional)", required = false, enabled = enabled,
        onValidityChange = onDateValidityChange)
    if (nextDate != null) PhmsTextButton(enabled = enabled, onClick = { onDate(null) }) { Text("Clear refill date") }
    Text("Refill details are entered by you, not supplied by your pharmacy.", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun MedicationPrescriptionDetails(med: Medication, providers: List<DoctorWithSpecialty>, pharmacies: List<Pharmacy>) {
    val context = LocalContext.current
    val provider = providers.firstOrNull { it.doctor.doctorId == med.doctorId }?.doctor
    val pharmacy = pharmacies.firstOrNull { it.pharmacyId == med.pharmacyId }
    provider?.let { Text("Prescribing provider: ${it.name}") }
    pharmacy?.let { Text("Pharmacy: ${it.name}") }
    med.prescriptionNumber?.takeIf(String::isNotBlank)?.let { Text("Rx #: $it") }
    med.refillsRemaining?.let { Text("Refills remaining: $it") }
    med.nextRefillAt?.let { Text("Next refill: " + SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(it))) }
    if (med.refillsRemaining != null || med.nextRefillAt != null)
        Text("Refill details are entered by you.", style = MaterialTheme.typography.bodySmall)
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure {
            Toast.makeText(context, "No app is available for this action.", Toast.LENGTH_SHORT).show()
        }
    }
    if (pharmacy != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (pharmacy.phone.isNotBlank()) PhmsTextButton(onClick = {
            open(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", pharmacy.phone, null)))
        }) { Text("Call pharmacy") }
        if (pharmacy.street.isNotBlank()) PhmsTextButton(onClick = {
            open(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(pharmacy.name + ", " + pharmacy.directionsAddress()))))
        }) { Text("Get directions") }
    }
}
