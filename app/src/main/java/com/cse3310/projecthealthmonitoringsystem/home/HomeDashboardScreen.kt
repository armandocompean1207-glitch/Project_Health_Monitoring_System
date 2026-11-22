package com.cse3310.projecthealthmonitoringsystem.home

import android.content.Context
import androidx.compose.runtime.*
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert
import com.cse3310.projecthealthmonitoringsystem.data.entity.AlertType
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseAlarms
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeDashboardSummary(
    val medication: HomeMedicationProgress? = null,
    val bloodPressure: HomeVitalRecord? = null,
    val glucose: HomeVitalRecord? = null,
    val cholesterol: HomeVitalRecord? = null,
    val nextAppointment: HomeAppointment? = null,
    val alert: HomeAlert? = null,
    val calories: Int = 0,
    val calorieGoal: Int = 0,
    val mealCount: Int = 0,
    val latestWeight: String? = null,
    val weightDate: String? = null,
    val heartRate: HomeVitalRecord? = null,
    val oxygen: HomeVitalRecord? = null,
    val weightHistory: List<Float> = emptyList(),
    val upcomingVisits: Int = 0,
    val activeAlerts: Int = 0,
)

data class HomeMedicationProgress(
    val takenDoses: Int,
    val totalDoses: Int,
    val nextMedicationName: String?,
    val nextDoseTime: String?,
    val instructions: String = "",
)

data class HomeVitalRecord(
    val displayValue: String,
    val unit: String,
    val recordedAt: String,
    val recentValues: List<Float> = emptyList(),
    val isAbnormal: Boolean = false,
)

data class HomeAppointment(
    val providerName: String,
    val dateAndTime: String,
)

data class HomeAlert(
    val title: String,
    val message: String,
    val isCritical: Boolean,
)

/** Home observes the same user-scoped repositories used by the feature screens. */
@Composable
fun HomeDashboardRoute(
    onUpdateProfileClick: () -> Unit,
    onMedicationClick: () -> Unit,
    onVitalsClick: () -> Unit,
    onAppointmentsClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onDietClick: () -> Unit = {},
    onWeightClick: () -> Unit = onDietClick,
    onNotesClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onMedicalFormsClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.phms()
    val userId = CurrentUser.userId
    val preferences = com.cse3310.projecthealthmonitoringsystem.settings.rememberMeasurementPreferences()

    val profile by app.authRepository.observeProfile(userId).collectAsState(initial = null)
    val medications by app.medicationRepository.observeActiveMedications(userId).collectAsState(initial = emptyList())
    val vitalReadings by app.vitalsRepository.observeVitalSigns(userId).collectAsState(initial = emptyList())
    val visits by app.maintenanceRepository.observeDoctorVisits(userId).collectAsState(initial = emptyList())
    val alerts by app.monitoringRepository.observeUnacknowledgedAlerts(userId).collectAsState(initial = emptyList())
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(60_000); value = System.currentTimeMillis() }
    }
    val todayRange = DoseTimes.todayRange()
    val calories by remember(userId, todayRange.first) { app.dietRepository.observeDailyCalorieTotal(userId, now) }.collectAsState(0)
    val meals by remember(userId, todayRange.first) { app.dietRepository.observeDietEntries(userId, todayRange.first, todayRange.second) }.collectAsState(emptyList())
    val weights by remember(userId) { app.dietRepository.observeWeights(userId) }.collectAsState(emptyList())
    val latestWeight = weights.maxByOrNull { it.recordedAt }
    val calorieGoal = context.getSharedPreferences("nutrition_goals", Context.MODE_PRIVATE).getInt("calories_$userId", 0)
    val doses by app.medicationRepository.observeDoseLog(userId, todayRange.first, todayRange.second)
        .collectAsState(initial = emptyList())

    val plannedDoses = medications.flatMap { med ->
        com.cse3310.projecthealthmonitoringsystem.medication.scheduledIntakes(med, todayRange.first, todayRange.second)
            .map { med.medicationId to it }
    }
    val doseCount = (plannedDoses + doses.map { it.dose.medicationId to it.dose.scheduledAt }).toSet().size
    val nextMedication = medications.filter { it.endDate == null || it.endDate >= DoseAlarms.nextReminderTime(it) }
        .minByOrNull { DoseAlarms.nextReminderTime(it) }
    val medicationProgress = doseCount.takeIf { it > 0 || nextMedication != null }?.let {
        HomeMedicationProgress(
            takenDoses = doses.count { it.dose.status == DoseStatus.TAKEN },
            totalDoses = doseCount,
            nextMedicationName = nextMedication?.let { "${it.name} ${it.dosage}" },
            instructions = nextMedication?.instructions.orEmpty(),
            nextDoseTime = nextMedication?.let {
                SimpleDateFormat("EEE, h:mm a", Locale.US)
                    .format(Date(DoseAlarms.nextReminderTime(it)))
            },
        )
    }

    fun latestVital(query: String): HomeVitalRecord? {
        val matches = vitalReadings.filter { it.typeName.contains(query, ignoreCase = true) }.sortedByDescending { it.vital.recordedAt }
        val latest = matches.firstOrNull() ?: return null
        val value = if (query == "blood pressure" && latest.vital.secondaryValue != null) {
            "${formatReading(latest.vital.value)}/${formatReading(latest.vital.secondaryValue)}"
        } else {
            formatReading(latest.vital.value)
        }
        return HomeVitalRecord(
            displayValue = value,
            unit = latest.unit,
            recordedAt = SimpleDateFormat("MMM d", Locale.US)
                .format(Date(latest.vital.recordedAt)),
            recentValues = matches.take(7).asReversed().map { it.vital.value.toFloat() },
            isAbnormal = latest.vital.isAbnormal,
        )
    }

    val nextAppointment = visits
        .filter { it.visit.visitAt >= System.currentTimeMillis() }
        .minByOrNull { it.visit.visitAt }
        ?.let { visit ->
            HomeAppointment(
                providerName = visit.doctorName,
                dateAndTime = SimpleDateFormat("EEE, MMM d · h:mm a", Locale.US)
                    .format(Date(visit.visit.visitAt)),
            )
        }

    val summary = HomeDashboardSummary(
        medication = medicationProgress,
        bloodPressure = latestVital("blood pressure"),
        glucose = latestVital("glucose"),
        cholesterol = latestVital("cholesterol"),
        heartRate = latestVital("heart rate"),
        oxygen = latestVital("oxygen"),
        upcomingVisits = visits.count { it.visit.visitAt >= now },
        activeAlerts = alerts.size,
        nextAppointment = nextAppointment,
        alert = alerts.firstOrNull()?.toHomeAlert(),
        calories = calories,
        calorieGoal = calorieGoal,
        mealCount = meals.size,
        latestWeight = latestWeight?.let { "${formatReading(com.cse3310.projecthealthmonitoringsystem.data.repository.DietRepository.convert(it.value, it.unit, preferences.weightUnit))} ${preferences.weightUnit.name.lowercase()}" },
        weightDate = latestWeight?.let { SimpleDateFormat("MMM d", Locale.US).format(Date(it.recordedAt)) },
        weightHistory = latestWeight?.let { latest ->
            weights.filter { it.recordedAt in (now - 7 * DoseTimes.DAY_MILLIS)..now }.sortedBy { it.recordedAt }.map {
                com.cse3310.projecthealthmonitoringsystem.data.repository.DietRepository.convert(it.value, it.unit, preferences.weightUnit).toFloat()
            }
        }.orEmpty(),
    )

    HomeDashboardScreen(
        username = profile?.name?.substringBefore(" ")?.takeIf { it.isNotBlank() }.orEmpty(),
        onUpdateProfileClick = onUpdateProfileClick,
        onMedicationClick = onMedicationClick,
        onVitalsClick = onVitalsClick,
        onAppointmentsClick = onAppointmentsClick,
        onAlertsClick = onAlertsClick,
        summary = summary,
        onDietClick = onDietClick,
        onWeightClick = onWeightClick,
        onNotesClick = onNotesClick,
        onMoreClick = onMoreClick,
        onSearchClick = onSearchClick,
        onMedicalFormsClick = onMedicalFormsClick,
        onAlertSettingsClick = {
            val intent = if (android.os.Build.VERSION.SDK_INT >= 26)
                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}"))
            runCatching { context.startActivity(intent) }.onFailure {
                android.widget.Toast.makeText(context, "Open PHMS notification settings in your device settings.",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        },
        now = now,
    )
}

private fun Alert.toHomeAlert() = HomeAlert(
    title = when (type) {
        AlertType.ABNORMAL_VITAL -> "Reading needs attention"
        AlertType.MISSED_DOSE -> "Missed medication"
        AlertType.CONFLICT -> "Medication alert"
    },
    message = message,
    isCritical = type == AlertType.ABNORMAL_VITAL,
)

private fun formatReading(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(Locale.US, value)
