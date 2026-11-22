package com.cse3310.projecthealthmonitoringsystem.healthguide

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A guide to the user's recorded care, not a clinical score or an invented recommendation. */
@Composable
fun HealthGuideScreen(onBack: () -> Unit, onVitals: () -> Unit, onMedicines: () -> Unit,
    onAppointments: () -> Unit, onNotes: () -> Unit, onCheckups: () -> Unit = onAppointments) {
    val app = LocalContext.current.phms()
    val locale = Locale.US
    val id = CurrentUser.userId
    val profile by remember(id) { app.authRepository.observeProfile(id) }.collectAsState(null)
    val readings by remember(id) { app.vitalsRepository.observeVitalSigns(id) }.collectAsState(emptyList())
    val medicines by remember(id) { app.medicationRepository.observeActiveMedications(id) }.collectAsState(emptyList())
    val visits by remember(id) { app.maintenanceRepository.observeDoctorVisits(id) }.collectAsState(emptyList())
    val now = System.currentTimeMillis()
    val upcoming = visits.count { it.visit.visitAt >= now }
    PhmsPage {
        PhmsScreenHeader("Health Guide", "A few simple ways to keep your care organized.", PhmsIcons.WellGuide, onBack)
        profile?.annualCheckupAt?.let { at ->
            PhmsActionCard("Annual checkup",
                "Saved date: " + SimpleDateFormat("MMM d, yyyy", locale).format(Date(at)),
                PhmsIcons.Appointments, onCheckups)
        }
        PhmsActionCard("Your checkups", "Review annual visits or set your next checkup date.", PhmsIcons.WellGuide, onCheckups)
        PhmsSectionHeader("Your care at a glance")
        PhmsActionCard("Record a reading",
            if (readings.isEmpty()) "Start with a measurement you've taken." else "${readings.size} readings saved · View your history.",
            PhmsIcons.Vitals, onVitals)
        PhmsActionCard("Review your medicines",
            if (medicines.isEmpty()) "Add the medicines prescribed to you." else "${medicines.size} active medicines · Check your schedule.",
            PhmsIcons.Medication, onMedicines)
        PhmsActionCard("Plan your next visit",
            if (upcoming == 0) "Keep your doctor's appointment details in one place." else "$upcoming upcoming appointments.",
            PhmsIcons.Appointments, onAppointments)
        PhmsActionCard("Write down a question", "Keep questions ready for your next visit.", PhmsIcons.Notes, onNotes)
        Text("Follow the advice of your healthcare professional. PHMS does not provide a diagnosis or replace medical care.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
