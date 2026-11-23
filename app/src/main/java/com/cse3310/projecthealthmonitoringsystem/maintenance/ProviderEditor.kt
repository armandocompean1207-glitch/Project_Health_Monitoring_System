package com.cse3310.projecthealthmonitoringsystem.maintenance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.Doctor
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Modal addition keeps the underlying medication draft and navigation entry intact. */
@Composable
internal fun ProviderEditor(original: Doctor, onDismiss: () -> Unit, onSaved: (Long) -> Unit) {
    val repository = LocalContext.current.phms().maintenanceRepository
    val specialties by remember { repository.observeSpecialties() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf(original) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf(false) }
    val dismiss = rememberGuardedDismiss(p != original, busy, onDismiss)
    AlertDialog(onDismissRequest = dismiss, title = { Text("Add provider") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PhmsTextField(p.name, { p = p.copy(name = it) }, label = { Text("Provider name") }, enabled = !busy, singleLine = true)
            PhmsTextField(p.providerType, { p = p.copy(providerType = it) }, label = { Text("Provider type (optional)") }, enabled = !busy, singleLine = true)
            Box {
                PhmsOutlinedButton(enabled = !busy, onClick = { choosing = true }) {
                    Text(specialties.firstOrNull { it.specialtyId == p.specialtyId }?.name ?: "Specialty (optional)")
                }
                DropdownMenu(choosing, onDismissRequest = { choosing = false }) {
                    DropdownMenuItem(text = { Text("None") }, onClick = { p = p.copy(specialtyId = null); choosing = false })
                    specialties.forEach { s -> DropdownMenuItem(text = { Text(s.name) },
                        onClick = { p = p.copy(specialtyId = s.specialtyId); choosing = false }) }
                }
            }
            PhmsTextField(p.clinicName, { p = p.copy(clinicName = it) }, label = { Text("Clinic or hospital (optional)") }, enabled = !busy, singleLine = true)
            PhmsTextField(p.address, { p = p.copy(address = it) }, label = { Text("Address (optional)") }, enabled = !busy, singleLine = true)
            PhmsTextField(p.phone, { p = p.copy(phone = it) }, label = { Text("Phone (optional)") }, enabled = !busy, singleLine = true)
            PhmsTextField(p.email, { p = p.copy(email = it) }, label = { Text("Email (optional)") }, enabled = !busy, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { PhmsTextButton(enabled = !busy, onClick = {
        busy = true; error = null
        scope.launch {
            try { onSaved(repository.addDoctor(p.userId, p.copy(name = p.name.trim()))) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }) { Text(if (busy) "Saving…" else "Save provider") } },
        dismissButton = { PhmsTextButton(enabled = !busy, onClick = dismiss) { Text("Cancel") } })
}
