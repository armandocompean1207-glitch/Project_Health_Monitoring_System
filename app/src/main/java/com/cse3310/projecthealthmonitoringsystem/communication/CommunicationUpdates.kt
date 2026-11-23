package com.cse3310.projecthealthmonitoringsystem.communication

import com.cse3310.projecthealthmonitoringsystem.data.dao.VitalSignWithType
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert
import com.cse3310.projecthealthmonitoringsystem.data.entity.AlertType
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import com.cse3310.projecthealthmonitoringsystem.monitoring.presentAlert
import com.cse3310.projecthealthmonitoringsystem.settings.MeasurementPreferences

/** Short selection labels deliberately omit values, dates and guidance. */
fun updateTitle(alert: Alert, reading: VitalSignWithType?, type: VitalType?): String = when (alert.type) {
    AlertType.MISSED_DOSE -> "Missed Medication"
    AlertType.CONFLICT -> "Medication Interaction"
    AlertType.ABNORMAL_VITAL -> {
        val source = reading?.takeIf { it.vital.vitalId == alert.vitalId && it.vital.userId == alert.userId }
        val name = source?.typeName.orEmpty()
        val value = source?.vital?.value
        when {
            name.contains("pressure", true) && source != null && type != null -> {
                val high = value!! > type.normalMax || source.vital.secondaryValue?.let { second ->
                    type.secondaryMax?.let { second > it }
                } == true
                if (high) "High Blood Pressure" else "Low Blood Pressure"
            }
            name.contains("glucose", true) && value != null && value < 70 -> "Low Blood Glucose"
            name.contains("glucose", true) -> "Abnormal Blood Glucose"
            name.isNotBlank() -> "Abnormal $name"
            else -> "Abnormal Vital Sign"
        }
    }
}

fun updatePreview(alert: Alert, reading: VitalSignWithType?, preferences: MeasurementPreferences): String {
    val content = presentAlert(alert, reading, preferences)
    return listOfNotNull(content.title, content.recorded, content.guidance).joinToString("\n\n")
}
