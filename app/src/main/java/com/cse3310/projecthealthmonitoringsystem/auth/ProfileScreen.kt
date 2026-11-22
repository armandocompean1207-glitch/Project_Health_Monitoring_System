package com.cse3310.projecthealthmonitoringsystem.auth

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextField as OutlinedTextField

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsOutlinedButton as OutlinedButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as OutlinedCard
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.Profile
import com.cse3310.projecthealthmonitoringsystem.data.entity.WeightUnit
import com.cse3310.projecthealthmonitoringsystem.monitoring.*
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.cse3310.projecthealthmonitoringsystem.settings.*
import com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
import com.cse3310.projecthealthmonitoringsystem.data.repository.DietRepository

@Composable
fun ProfileScreen(onBack: () -> Unit, onDoctors: () -> Unit, onAppointments: () -> Unit) {
    val context = LocalContext.current
    val prefs = rememberMeasurementPreferences()
    val id = CurrentUser.userId
    val repository = context.phms().authRepository
    val profile by remember(id) { repository.observeProfile(id) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmLogout by remember { mutableStateOf(false) }
    PhmsPage(Modifier.imePadding()) {
        if (!editing) PhmsScreenHeader("Personal profile", "Keep your personal details up to date.", PhmsIcons.Profile, onBack)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val current = profile
        if (current == null) {
            CircularProgressIndicator()
        } else if (editing) {
            ProfileEditor(current, busy, onCancel = { editing = false }) { updated ->
                busy = true; error = null
                scope.launch {
                    try {
                        check(repository.updateProfile(id, updated)) { "Unable to update this profile." }
                        editing = false
                    } catch (e: CancellationException) { throw e
                    } catch (e: Exception) { error = e.message
                    } finally { busy = false }
                }
            }
        } else {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhmsIconBadge(PhmsIcons.Profile)
                    Text(current.name, style = MaterialTheme.typography.headlineMedium)
                    PhmsStatusPill("Your private profile")
                    Text("Gender: ${current.gender.ifBlank { "Not provided" }}")
                    Text("Age: ${current.dateOfBirth?.let { HealthValidation.age(it).toString() } ?: current.age.takeIf { it > 0 }?.toString() ?: "Not provided"}")
                    current.dateOfBirth?.let { Text("Date of birth: " + SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(HealthValidation.parseBirthDate(it)!!))) }
                    Text("Height: " + if (current.heightCm <= 0) "Not provided" else if (prefs.imperialHeight) {
                        val inches = kotlin.math.round(MeasurementUnits.inches(current.heightCm)).toInt()
                        "${inches / 12} ft ${inches % 12} in"
                    } else String.format(Locale.US, "%.1f cm", current.heightCm))
                    Text("Weight: " + if (current.weight <= 0) "Not provided" else String.format(Locale.US, "%.1f %s", DietRepository.convert(current.weight, current.weightUnit, prefs.weightUnit), prefs.weightUnit.name.lowercase()))
                    Text("Phone: ${current.phone.ifBlank { "Not provided" }}")
                    Button(onClick = { editing = true }) { Text("Edit personal details") }
                }
            }
        }
        PhmsActionCard("Your doctors", "View and manage your care team.", PhmsIcons.DataMaintenance, onDoctors)
        PhmsActionCard("Appointments", "View your saved doctor visits.", PhmsIcons.Appointments, onAppointments)
        OutlinedButton(onClick = { confirmLogout = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
    }
    if (confirmLogout) AlertDialog(onDismissRequest = { if (!busy) confirmLogout = false },
        title = { Text("Log out?") }, text = { Text("Your records stay saved. Reminders for this account pause until you sign in again.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch {
                try { AccountSession.logout(context) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message; confirmLogout = false }
                finally { busy = false }
            }
        }) { Text("Log out") } }, dismissButton = { TextButton(enabled = !busy, onClick = { confirmLogout = false }) { Text("Cancel") } })
}

@Composable
private fun ProfileEditor(profile: Profile, busy: Boolean, onCancel: () -> Unit, onSave: (Profile) -> Unit) {
    val context = LocalContext.current
    val prefs = rememberMeasurementPreferences()
    var name by remember { mutableStateOf(profile.name) }
    var gender by remember { mutableStateOf(profile.gender) }
    var birth by remember { mutableStateOf(profile.dateOfBirth) }
    var cm by remember { mutableStateOf(profile.heightCm.takeIf { it > 0 }?.toString() ?: "") }
    val initialInches = kotlin.math.round(MeasurementUnits.inches(profile.heightCm)).toInt()
    var feet by remember { mutableStateOf(if (profile.heightCm > 0) (initialInches / 12).toString() else "") }
    var inches by remember { mutableStateOf(if (profile.heightCm > 0) (initialInches % 12).toString() else "") }
    val initialWeight = if (profile.weight > 0) String.format(Locale.US, "%.2f", DietRepository.convert(profile.weight, profile.weightUnit, prefs.weightUnit)) else ""
    var weight by remember { mutableStateOf(initialWeight) }
    var phone by remember { mutableStateOf(profile.phone) }
    var attempted by remember { mutableStateOf(false) }
    val heightValue = if (prefs.imperialHeight) MeasurementUnits.centimeters(feet.toDoubleOrNull() ?: Double.NaN, inches.toDoubleOrNull() ?: Double.NaN)
        else cm.toDoubleOrNull() ?: Double.NaN
    val weightValue = weight.toDoubleOrNull() ?: Double.NaN
    val heightError = if (cm.isBlank() && feet.isBlank() && inches.isBlank()) null
        else if (prefs.imperialHeight && (inches.toDoubleOrNull()?.let { it !in 0.0..<12.0 } != false)) "Inches must be between 0 and 11.9."
        else HealthValidation.heightCm(heightValue)
    val weightError = if (weight.isBlank()) null else HealthValidation.weightKg(
        DietRepository.convert(weightValue, prefs.weightUnit, WeightUnit.KG))
    val nameError = HealthValidation.name(name)
    val phoneError = HealthValidation.phone(phone)
    val birthError = birth?.let { HealthValidation.birthDate(it) }
    val valid = listOfNotNull(nameError, phoneError, birthError, heightError, weightError).isEmpty()
    val dismiss = rememberGuardedDismiss(true, busy, onCancel)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PhmsScreenHeader("Edit personal details", icon = PhmsIcons.Profile, onBack = dismiss)
        OutlinedTextField(name, { name = it }, label = { Text("Full name") }, enabled = !busy,
            isError = attempted && nameError != null, supportingText = { if (attempted) nameError?.let { Text(it) } }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(gender, { gender = it }, label = { Text("Gender (optional)") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedButton(enabled = !busy, onClick = {
            val c = java.util.Calendar.getInstance().apply { birth?.let { timeInMillis = HealthValidation.parseBirthDate(it) ?: timeInMillis } }
            android.app.DatePickerDialog(context, { _, y, m, d -> birth = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d) },
                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH), c.get(java.util.Calendar.DAY_OF_MONTH))
                .apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        }, modifier = Modifier.fillMaxWidth()) { Icon(PhmsIcons.Appointments, null); Text(birth?.let { "Date of birth: $it" } ?: "Choose date of birth", Modifier.padding(start = 8.dp)) }
        birth?.let { Text("Age ${HealthValidation.age(it)}", style = MaterialTheme.typography.bodyMedium) }
            ?: Text("Your previous age is kept until you add a date of birth.", style = MaterialTheme.typography.bodyMedium)
        if (prefs.imperialHeight) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(feet, { feet = it }, label = { Text("Height (ft)") }, enabled = !busy, modifier = Modifier.weight(1f),
                isError = attempted && heightError != null, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            OutlinedTextField(inches, { inches = it }, label = { Text("Inches") }, enabled = !busy, modifier = Modifier.weight(1f),
                isError = attempted && heightError != null, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
        } else OutlinedTextField(cm, { cm = it }, label = { Text("Height (cm)") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        if (attempted) heightError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(weight, { weight = it }, label = { Text("Weight (${prefs.weightUnit.name.lowercase()})") }, enabled = !busy,
            isError = attempted && weightError != null, supportingText = { if (attempted) weightError?.let { Text(it) } },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Phone (optional)") }, enabled = !busy,
            isError = attempted && phoneError != null, supportingText = { if (attempted) phoneError?.let { Text(it) } }, modifier = Modifier.fillMaxWidth())
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            attempted = true
            if (valid) onSave(profile.copy(name = name.trim(), gender = gender.trim(), dateOfBirth = birth,
                age = birth?.let { HealthValidation.age(it) } ?: profile.age,
                heightCm = if (if (prefs.imperialHeight) feet == (initialInches / 12).toString() && inches == (initialInches % 12).toString()
                    else cm == profile.heightCm.toString()) profile.heightCm else if (heightValue.isFinite()) heightValue else 0.0,
                weight = if (weight == initialWeight) profile.weight else if (weightValue.isFinite()) weightValue else 0.0,
                weightUnit = if (weight == initialWeight) profile.weightUnit else prefs.weightUnit, phone = phone.trim()))
        }) { Text(if (busy) "Saving…" else "Save profile") }
        TextButton(enabled = !busy, onClick = dismiss) { Text("Cancel") }
    }
}

@Composable
fun AppointmentsScreen(onBack: () -> Unit, onManage: () -> Unit) {
    val context = LocalContext.current
    val id = CurrentUser.userId
    val repository = context.phms().maintenanceRepository
    val visits by remember(id) { repository.observeDoctorVisits(id) }.collectAsState(emptyList())
    val doctors by remember(id) { repository.observeDoctors(id) }.collectAsState(emptyList())
    PhmsPage {
        PhmsScreenHeader("Appointments", icon = PhmsIcons.Appointments, onBack = onBack)
        if (visits.isEmpty()) PhmsEmptyStateCard("No visits saved", "Your doctor visits will appear here when added by your care forms.",
            PhmsIcons.Appointments, actionLabel = "Manage doctors", onAction = onManage)
        visits.forEach {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(it.doctorName, style = MaterialTheme.typography.titleMedium)
                    Text(SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(it.visit.visitAt)))
                }
            }
        }
        PhmsSectionHeader("Your doctors")
        doctors.forEach {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(it.doctor.name, style = MaterialTheme.typography.titleMedium)
                    it.specialtyName?.let { name -> Text(name) }
                    Text(it.doctor.phone)
                    Text(it.doctor.email)
                }
            }
        }
        Button(onClick = onManage) { Text("Manage care team") }
    }
}
