package com.cse3310.projecthealthmonitoringsystem.monitoring

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as OutlinedCard
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.dao.VitalSignWithType
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert
import com.cse3310.projecthealthmonitoringsystem.data.entity.AlertType
import com.cse3310.projecthealthmonitoringsystem.settings.MeasurementPreferences
import com.cse3310.projecthealthmonitoringsystem.settings.MeasurementUnits
import com.cse3310.projecthealthmonitoringsystem.validation.GlucoseContext
import com.cse3310.projecthealthmonitoringsystem.validation.VitalAssessment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun alertDate(time: Long): String = SimpleDateFormat("MM/dd/yyyy · h:mm a", Locale.US).format(Date(time))

/** Reword legacy alert text at display time without rewriting stored records. */
fun updatedEmergencyGuidance(text: String): String =
    Regex("\\bcall\\s+911\\b", RegexOption.IGNORE_CASE).replace(text) { match ->
        if (match.value.first().isUpperCase()) "Seek emergency medical care immediately"
        else "seek emergency medical care immediately"
    }

/** Presentation only: saved values, abnormal flags and original alert messages remain unchanged. */
data class PresentedAlert(val title: String, val guidance: String, val recorded: String?, val vital: Boolean)

fun presentAlert(alert: Alert, reading: VitalSignWithType?, prefs: MeasurementPreferences): PresentedAlert {
    if (alert.type != AlertType.ABNORMAL_VITAL)
        return PresentedAlert(AlertHandoff.title(alert.type), updatedEmergencyGuidance(alert.message), "Recorded on ${alertDate(alert.createdAt)}", false)
    val source = reading?.takeIf { it.vital.vitalId == alert.vitalId && it.vital.userId == alert.userId }
        ?: return PresentedAlert("Health reading alert", "The original reading is no longer available.", null, true)
    val v = source.vital
    val displayed = MeasurementUnits.displayVital(v.value, source.typeName, source.unit, prefs)
    val unit = MeasurementUnits.displayUnit(source.typeName, source.unit, prefs)
    val value = VitalAssessment.number(displayed, MeasurementUnits.temperature(source.typeName)) +
        (v.secondaryValue?.let { "/${VitalAssessment.number(it)}" } ?: "")
    val title = if (v.isAbnormal) "Abnormal ${source.typeName} Detected: $value $unit"
        else "Corrected ${source.typeName}: $value $unit"
    val guidance = if (!v.isAbnormal) "This reading was corrected. The alert is resolved."
        else vitalAlertGuidance(source)
    return PresentedAlert(title, guidance, "Recorded on ${alertDate(v.recordedAt)}", true)
}

private fun vitalAlertGuidance(source: VitalSignWithType): String {
    val v = source.vital
    val name = source.typeName.lowercase(Locale.US)
    // Reuse the established safety thresholds; temperature guidance avoids a second display unit.
    // AHA: https://www.heart.org/en/health-topics/high-blood-pressure/understanding-blood-pressure-readings/monitoring-your-blood-pressure-at-home
    // NIH: https://medlineplus.gov/lab-tests/pulse-oximetry/
    // CDC: https://www.cdc.gov/diabetes/treatment/treatment-low-blood-sugar-hypoglycemia.html
    val urgent = VitalAssessment.urgentAdvice(source.typeName, source.unit, v.value, v.secondaryValue)
    if (urgent != null) return if (name.contains("temperature") &&
        MeasurementUnits.displayVital(v.value, source.typeName, source.unit, MeasurementPreferences(false)) < 35)
        "Very low body temperature can be a medical emergency. Seek immediate medical attention. Seek emergency medical care immediately for confusion or reduced responsiveness."
        else urgent
    return when {
        name.contains("pressure") -> "Rest quietly and recheck your blood pressure. Contact your clinician if it stays outside your target."
        name.contains("glucose") -> {
            val context = GlucoseContext.fromNote(v.note)
            val advice = if (v.value < 70) "Follow your prescribed low-blood-sugar plan and recheck. Get help if it does not improve."
                else "Recheck your blood sugar and follow your care plan. Contact your clinician if it stays outside your target."
            if (context == GlucoseContext.SAVED_RANGE) advice else "${context.label}. $advice"
        }
        name.contains("temperature") -> "Recheck with your thermometer. Contact your clinician if this persists or you feel unwell."
        name.contains("cholesterol") -> "Review this result with your clinician and follow your cholesterol care plan."
        name.contains("heart") || name.contains("pulse") ->
            "Rest and recheck your pulse. Seek emergency medical care immediately for chest pain, fainting or trouble breathing."
        name.contains("oxygen") -> "Recheck with a steady hand. Contact your clinician if your oxygen reading stays below your target."
        name.contains("respiratory") -> "Recheck your breathing rate at rest. Seek emergency medical care immediately for severe trouble breathing."
        name.contains("pain") -> "Follow your pain care plan. Seek urgent help for sudden severe pain or chest pain."
        else -> "Recheck this reading and contact your clinician if it stays outside your target."
    }
}

/** Shared between the Home warning and Monitoring, including large-font action wrapping. */
@Composable
fun AlertActions(onDetails: (() -> Unit)?, onAcknowledge: (() -> Unit)?, onNotify: () -> Unit) {
    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = ButtonDefaults.outlinedButtonColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFF065F46),
            contentColor = androidx.compose.ui.graphics.Color.White,
            disabledContainerColor = androidx.compose.ui.graphics.Color(0xFF065F46))
        val border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF065F46))
        if (onDetails != null) androidx.compose.material3.OutlinedButton(onClick = onDetails,
            colors = colors, border = border, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.widthIn(min = 65.dp * scale).weight(1f).heightIn(min = 48.dp)) { Text("View", maxLines = 1, softWrap = false) }
        androidx.compose.material3.OutlinedButton(enabled = onAcknowledge != null,
            onClick = { onAcknowledge?.invoke() }, colors = colors, border = border,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.widthIn(min = 126.dp * scale).weight(1.65f).heightIn(min = 48.dp)) { Text("Acknowledge", maxLines = 1, softWrap = false) }
        androidx.compose.material3.OutlinedButton(onClick = onNotify, colors = colors, border = border,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier.widthIn(min = 73.dp * scale).weight(1f).heightIn(min = 48.dp)) { Text("Notify", maxLines = 1, softWrap = false) }
    }
}

@Composable
fun AlertHeading(title: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.WarningAmber, "Health alert", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f))
    }
}

@Composable
fun NoDesignatedPersonDialog(onDismiss: () -> Unit, onAdd: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add a designated person") },
        text = { Text("Add someone you trust before sharing this alert. You will choose a recipient and confirm before sending.") },
        confirmButton = { TextButton(onClick = onAdd) { Text("Add person") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } })
}

@Composable
fun AlertSummary(content: PresentedAlert, onDetails: () -> Unit,
    onAcknowledge: (() -> Unit)? = null, onNotify: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AlertHeading(content.title)
            Text(content.guidance, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
            content.recorded?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            AlertActions(onDetails, onAcknowledge, onNotify)
        }
    }
}
