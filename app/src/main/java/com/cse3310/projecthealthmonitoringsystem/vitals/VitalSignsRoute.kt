package com.cse3310.projecthealthmonitoringsystem.vitals

import androidx.compose.runtime.*
import com.cse3310.projecthealthmonitoringsystem.settings.*
import androidx.compose.ui.platform.LocalContext
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton
import com.cse3310.projecthealthmonitoringsystem.validation.GlucoseContext
import com.cse3310.projecthealthmonitoringsystem.validation.VitalAssessment
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert

@Composable
fun VitalSignsRoute(onNotifyContact: (Long) -> Unit = {}) {
    val app = LocalContext.current.phms()
    val repository = app.vitalsRepository
    val userId = CurrentUser.userId
    val prefs = rememberMeasurementPreferences()
    val profile by remember(app, userId) { app.authRepository.observeProfile(userId) }.collectAsState(initial = null)
    val types by remember(repository) { repository.observeVitalTypes() }.collectAsState(emptyList())
    val records by remember(repository, userId) { repository.observeVitalSigns(userId) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var savedVersion by remember { mutableIntStateOf(0) }
    var savedAlert by remember(userId) { mutableStateOf<Alert?>(null) }

    VitalSignsScreen(
        patientName = profile?.name.orEmpty(),
        vitalTypes = types.map {
            VitalTypeOption(it.vitalTypeId, it.name, MeasurementUnits.displayUnit(it.name, it.unit, prefs),
                if (it.secondaryLabel != null) "Systolic" else "Reading", it.secondaryLabel,
                MeasurementUnits.displayVital(it.normalMin, it.name, it.unit, prefs),
                MeasurementUnits.displayVital(it.normalMax, it.name, it.unit, prefs), it.secondaryMin, it.secondaryMax)
        },
        readings = records.map {
            VitalReadingItem(it.typeName, VitalAssessment.number(MeasurementUnits.displayVital(it.vital.value, it.typeName, it.unit, prefs),
                MeasurementUnits.temperature(it.typeName)), MeasurementUnits.displayUnit(it.typeName, it.unit, prefs),
                it.vital.secondaryValue?.let { second -> VitalAssessment.number(second) }, VitalAssessment.date(it.vital.recordedAt),
                it.vital.isAbnormal, it.vital.recordedAt,
                GlucoseContext.fromNote(it.vital.note).takeIf { c -> c != GlucoseContext.SAVED_RANGE }?.label,
                it.vital.vitalId, MeasurementUnits.displayVital(it.vital.value, it.typeName, it.unit, prefs), it.vital.secondaryValue)
        },
        saving = saving,
        saveMessage = message,
        saveError = saveError,
        savedVersion = savedVersion,
        onMessageShown = { message = null },
        onSaveReading = { request ->
            if (!saving) {
                saving = true
                message = null
                saveError = null
                scope.launch {
                    try {
                        val result = repository.addEnteredVitalSign(userId, request.typeId, request.value,
                            request.unit, request.second, request.recordedAt, request.context)
                        if (result == null) {
                            saveError = "This measurement type is no longer available."
                        } else {
                            savedVersion++
                            savedAlert = result.alertId?.let { app.monitoringRepository.getAlert(userId, it) }
                            message = if (result.isAbnormal)
                                "Reading saved. Please review your health alert."
                            else "Reading saved."
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        saveError = e.message ?: "Couldn't save your reading. Please try again."
                    } finally {
                        saving = false
                    }
                }
            }
        }
    )
    savedAlert?.let { alert ->
        AlertDialog(onDismissRequest = { savedAlert = null }, title = { Text("Reading saved · Health alert") },
            text = { Text(com.cse3310.projecthealthmonitoringsystem.monitoring.updatedEmergencyGuidance(alert.message) + "\n\nWould you like to share this with a trusted contact?") },
            confirmButton = { PhmsTextButton(onClick = {
                savedAlert = null
                onNotifyContact(alert.alertId)
            }) { Text("Choose contact") } },
            dismissButton = { PhmsTextButton(onClick = { savedAlert = null }) { Text("Not now") } })
    }
}
