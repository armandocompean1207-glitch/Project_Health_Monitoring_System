package com.cse3310.projecthealthmonitoringsystem.vitals

import androidx.compose.runtime.*
import com.cse3310.projecthealthmonitoringsystem.settings.*
import androidx.compose.ui.platform.LocalContext
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VitalSignsRoute() {
    val repository = LocalContext.current.phms().vitalsRepository
    val userId = CurrentUser.userId
    val prefs = rememberMeasurementPreferences()
    val types by remember(repository) { repository.observeVitalTypes() }.collectAsState(emptyList())
    val records by remember(repository, userId) { repository.observeVitalSigns(userId) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var savedVersion by remember { mutableIntStateOf(0) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US) }

    VitalSignsScreen(
        vitalTypes = types.map {
            VitalTypeOption(it.vitalTypeId, it.name, MeasurementUnits.displayUnit(it.name, it.unit, prefs),
                if (it.secondaryLabel != null) "Systolic" else "Reading", it.secondaryLabel,
                MeasurementUnits.displayVital(it.normalMin, it.name, it.unit, prefs),
                MeasurementUnits.displayVital(it.normalMax, it.name, it.unit, prefs), it.secondaryMin, it.secondaryMax)
        },
        readings = records.map {
            VitalReadingItem(it.typeName, String.format(Locale.US, "%.1f", MeasurementUnits.displayVital(it.vital.value, it.typeName, it.unit, prefs)).removeSuffix(".0"), MeasurementUnits.displayUnit(it.typeName, it.unit, prefs),
                it.vital.secondaryValue?.toString(), dateFormat.format(Date(it.vital.recordedAt)),
                it.vital.isAbnormal, it.vital.recordedAt)
        },
        saving = saving,
        saveMessage = message,
        saveError = saveError,
        savedVersion = savedVersion,
        onMessageShown = { message = null },
        onSaveReading = { typeId, value, secondary ->
            if (!saving) {
                saving = true
                message = null
                saveError = null
                scope.launch {
                    try {
                        val type = types.first { it.vitalTypeId == typeId }
                        val result = repository.addVitalSign(userId, typeId,
                            MeasurementUnits.storedVital(value, type.name, type.unit, prefs), secondary)
                        if (result == null) {
                            saveError = "This measurement type is no longer available."
                        } else {
                            savedVersion++
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
}
