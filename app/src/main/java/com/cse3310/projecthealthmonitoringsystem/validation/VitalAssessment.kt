package com.cse3310.projecthealthmonitoringsystem.validation

import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import com.cse3310.projecthealthmonitoringsystem.settings.MeasurementPreferences
import com.cse3310.projecthealthmonitoringsystem.settings.MeasurementUnits
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Explicit context; unspecified/legacy readings retain the editable project reference range. */
enum class GlucoseContext(val label: String) {
    SAVED_RANGE("Use saved reference range"),
    FASTING("Fasting (without diabetes)"),
    BEFORE_MEAL("Before a meal (diabetes target)"),
    AFTER_MEAL("1–2 hours after a meal (diabetes target)");

    fun note() = if (this == SAVED_RANGE) "" else "PHMS_GLUCOSE_CONTEXT=$name\n"
    companion object {
        fun fromNote(note: String) = entries.firstOrNull {
            it != SAVED_RANGE && note.startsWith(it.note())
        } ?: SAVED_RANGE
    }
}

/** UI confirmations and persisted flags use exactly the same units and comparison policy. */
object VitalAssessment {
    // These are reference/management targets, never input rejection limits.
    // Fasting: https://www.cdc.gov/diabetes/diabetes-testing/index.html
    // Diabetes targets: https://www.cdc.gov/diabetes/diabetes-testing/monitoring-blood-sugar.html
    fun isAbnormal(type: VitalType, value: Double, second: Double?, context: GlucoseContext = GlucoseContext.SAVED_RANGE): Boolean {
        if (!type.name.contains("glucose", true) || type.unit != "mg/dL" || context == GlucoseContext.SAVED_RANGE)
            return type.isAbnormal(value, second)
        return when (context) {
            GlucoseContext.FASTING -> value < 70.0 || value > 99.0
            GlucoseContext.BEFORE_MEAL -> value < 80.0 || value > 130.0
            GlucoseContext.AFTER_MEAL -> value < 70.0 || value >= 180.0
            GlucoseContext.SAVED_RANGE -> type.isAbnormal(value, second)
        }
    }

    fun contextHelp(context: GlucoseContext) = when (context) {
        GlucoseContext.SAVED_RANGE -> "Uses your saved reference range. Meal timing can change how a glucose reading is interpreted."
        GlucoseContext.FASTING -> "Fasting reference: 70–99 mg/dL after at least 8 hours without food. Not a diagnosis."
        GlucoseContext.BEFORE_MEAL -> "General adult diabetes target: 80–130 mg/dL. Your clinician's target may differ."
        GlucoseContext.AFTER_MEAL -> "General adult diabetes target: below 180 mg/dL, 1–2 hours after the meal starts. Below 70 is low. Your target may differ."
    }

    fun number(value: Double, temperature: Boolean = false): String =
        if (temperature) String.format(Locale.US, "%.1f", value)
        else String.format(Locale.US, "%.1f", value).removeSuffix(".0")

    fun reading(name: String, unit: String, value: Double, second: Double? = null): String {
        if (MeasurementUnits.temperature(name)) {
            val f = MeasurementUnits.displayVital(value, name, unit, MeasurementPreferences())
            val c = MeasurementUnits.celsius(f)
            return "${number(f, true)} °F (${number(c, true)} °C)"
        }
        return "${number(value)}${second?.let { "/${number(it)}" } ?: ""} $unit".trim()
    }

    fun date(at: Long): String = SimpleDateFormat("MM/dd/yyyy · h:mm a", Locale.US).format(Date(at))

    fun alertMessage(name: String, unit: String, value: Double, second: Double?, recordedAt: Long,
        note: String, abnormal: Boolean): String = buildString {
        append(if (abnormal) "Abnormal " else "Corrected reading · ")
        append("$name: ${reading(name, unit, value, second)}")
        append("\nRecorded ${date(recordedAt)}")
        val context = GlucoseContext.fromNote(note)
        if (name.contains("glucose", true) && context != GlucoseContext.SAVED_RANGE) append("\n${context.label}")
        if (abnormal) urgentAdvice(name, unit, value, second)?.let { append("\n$it") }
        else append("\nThe linked reading was corrected; this alert is resolved.")
    }

    /**
     * Safety guidance, not diagnosis, and never used to reject a plausible reading.
     * AHA: heart.org/en/health-topics/high-blood-pressure/understanding-blood-pressure-readings/when-to-call-911-for-high-blood-pressure
     * NIH: medlineplus.gov/hypothermia.html and medlineplus.gov/lab-tests/pulse-oximetry/
     * CDC: cdc.gov/diabetes/treatment/treatment-low-blood-sugar-hypoglycemia.html
     * CDC: cdc.gov/diabetes/about/diabetic-ketoacidosis.html
     * NIH: medlineplus.gov/ency/article/003090.htm
     */
    fun urgentAdvice(name: String, unit: String, value: Double, second: Double?): String? {
        val key = name.lowercase(Locale.US)
        val c = if (unit.contains("F", true)) MeasurementUnits.celsius(value) else value
        return when {
            key.contains("pressure") && (value > 180 || (second ?: 0.0) > 120) ->
                "Recheck after 1 minute. If still this high, contact a clinician immediately. With chest pain, trouble breathing, weakness or vision changes, seek emergency medical care immediately."
            key.contains("oxygen") && value <= 88 ->
                "Seek immediate medical attention. With trouble breathing or confusion, seek emergency medical care immediately."
            key.contains("oxygen") && value <= 92 ->
                "Contact your healthcare provider promptly about this oxygen reading."
            key.contains("temperature") && c < 35 ->
                "A body temperature below 95 °F can be a medical emergency. Seek immediate medical attention; seek emergency medical care immediately for confusion or reduced responsiveness."
            key.contains("temperature") && c >= 39.4 ->
                "Contact a clinician promptly if the fever stays this high. Seek emergency medical care immediately for confusion, trouble breathing or reduced responsiveness."
            key.contains("glucose") && unit == "mg/dL" && value < 55 ->
                "Blood sugar is severely low. Follow your prescribed low-blood-sugar plan and get urgent help. Seek emergency medical care immediately for confusion, seizures, fainting or inability to swallow."
            key.contains("glucose") && unit == "mg/dL" && value >= 300 ->
                "Recheck and follow your diabetes care plan. If blood sugar stays at 300 mg/dL or higher, or you have vomiting or trouble breathing, seek emergency care."
            else -> null
        }
    }
}
