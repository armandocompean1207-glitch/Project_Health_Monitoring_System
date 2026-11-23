package com.cse3310.projecthealthmonitoringsystem.maintenance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import com.cse3310.projecthealthmonitoringsystem.validation.ProviderPharmacyValidation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CareContactIcon(icon: ImageVector) {
    Surface(shape = CircleShape, color = Color(0xFFFFDF48), contentColor = Color(0xFF111827)) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp)) }
    }
}

@Composable
internal fun PharmaciesSection() {
    val repository = LocalContext.current.phms().maintenanceRepository
    val userId = CurrentUser.userId
    val pharmacies by remember(userId) { repository.observePharmacies(userId) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember(userId) { mutableStateOf<Pharmacy?>(null) }
    var deleting by remember(userId) { mutableStateOf<Pharmacy?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { editing = Pharmacy(userId = userId, name = "") }) { Text("Add pharmacy") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (pharmacies.isEmpty()) PhmsEmptyStateCard("Add your first pharmacy",
            "Keep your pharmacy details handy for prescriptions and refills.", Icons.Outlined.LocalPharmacy)
        pharmacies.forEach { p ->
            PhmsCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CareContactIcon(Icons.Outlined.LocalPharmacy)
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                    }
                    if (p.isPreferred) Text("Preferred pharmacy", style = MaterialTheme.typography.labelLarge)
                    if (p.directionsAddress().isNotBlank()) Text(p.directionsAddress())
                    if (p.phone.isNotBlank()) Text(p.phone)
                    if (p.pharmacistName.isNotBlank()) Text("Pharmacist: ${p.pharmacistName}")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        PhmsTextButton(enabled = !busy, onClick = { editing = p }) { Text("Edit") }
                        if (!p.isPreferred) PhmsTextButton(enabled = !busy, onClick = {
                            busy = true; error = null
                            scope.launch {
                                try { check(repository.setPreferredPharmacy(userId, p.pharmacyId)) { "Pharmacy is unavailable." } }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = e.message }
                                finally { busy = false }
                            }
                        }) { Text("Set as preferred") }
                        PhmsTextButton(enabled = !busy, onClick = { deleting = p }) { Text("Delete") }
                    }
                }
            }
        }
    }
    editing?.let { pharmacy ->
        PharmacyEditor(pharmacy, onDismiss = { editing = null }) { editing = null }
    }
    deleting?.let { p ->
        AlertDialog(onDismissRequest = { if (!busy) deleting = null }, title = { Text("Delete ${p.name}?") },
            text = { Text("Medication records and dose history will be kept. Their pharmacy link will be cleared.") },
            confirmButton = { PhmsTextButton(enabled = !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    try { check(repository.deletePharmacy(userId, p.pharmacyId)) { "Pharmacy is unavailable." }; deleting = null }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message; deleting = null }
                    finally { busy = false }
                }
            }) { Text("Delete") } },
            dismissButton = { PhmsTextButton(enabled = !busy, onClick = { deleting = null }) { Text("Cancel") } })
    }
}

@Composable
internal fun PharmacyEditor(original: Pharmacy, onDismiss: () -> Unit, onSaved: (Long) -> Unit) {
    val repository = LocalContext.current.phms().maintenanceRepository
    val scope = rememberCoroutineScope()
    var p by remember(original) { mutableStateOf(original) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val dismiss = rememberGuardedDismiss(p != original, busy, onDismiss)
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (p.pharmacyId == 0L) "Add pharmacy" else "Edit pharmacy") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PhmsTextField(p.name, { p = p.copy(name = it) }, label = { Text("Pharmacy name") }, enabled = !busy, singleLine = true)
                PhmsTextField(p.street, { p = p.copy(street = it) }, label = { Text("Street address (optional)") }, enabled = !busy, singleLine = true)
                PhmsTextField(p.city, { p = p.copy(city = it) }, label = { Text("City (optional)") }, enabled = !busy, singleLine = true)
                PhmsTextField(p.state, { p = p.copy(state = it) }, label = { Text("State (optional, e.g. TX)") }, enabled = !busy, singleLine = true)
                PhmsTextField(p.zip, { p = p.copy(zip = it) }, label = { Text("ZIP code (optional)") }, enabled = !busy, singleLine = true)
                PhmsTextField(p.phone, { p = p.copy(phone = it) }, label = { Text("Phone number (optional)") }, enabled = !busy, singleLine = true)
                PhmsTextField(p.pharmacistName, { p = p.copy(pharmacistName = it) }, label = { Text("Pharmacist's name (optional)") }, enabled = !busy, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(p.isPreferred, { p = p.copy(isPreferred = it) }, enabled = !busy)
                    Text("Set as preferred pharmacy")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { PhmsTextButton(enabled = !busy, onClick = {
            error = ProviderPharmacyValidation.pharmacy(p)
            if (error == null) {
                busy = true
                val saved = p.copy(name = p.name.trim(), street = p.street.trim(), city = p.city.trim(),
                    state = p.state.trim().uppercase(java.util.Locale.US), zip = p.zip.trim(), phone = p.phone.trim(), pharmacistName = p.pharmacistName.trim())
                scope.launch {
                    try {
                        val id = if (saved.pharmacyId == 0L) repository.addPharmacy(saved.userId, saved)
                        else { check(repository.editPharmacy(saved.userId, saved)) { "Pharmacy is unavailable." }; saved.pharmacyId }
                        onSaved(id)
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = e.message }
                    finally { busy = false }
                }
            }
        }) { Text(if (busy) "Saving…" else "Save") } },
        dismissButton = { PhmsTextButton(enabled = !busy, onClick = dismiss) { Text("Cancel") } })
}
