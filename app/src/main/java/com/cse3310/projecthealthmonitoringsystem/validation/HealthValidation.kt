package com.cse3310.projecthealthmonitoringsystem.validation

import java.util.Calendar
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Locale

/**
 * Input envelopes, NOT clinical reference ranges or diagnostic thresholds.
 * Broad software capture limits intentionally allow abnormal readings. Monitoring still uses VitalType.
 * BP 0..500 covers the 0..299 cuff range and wider manually transcribed pressure readings;
 * glucose 0..2000 extends beyond common meter ranges (20..600) to allow lab results.
 * Temperature 10..50 Celsius allows extreme/low-body-temperature recordings, not environmental readings.
 * HR/RR caps (500/300) guard magnitude/decimal-entry mistakes; oxygen is a percentage and pain a 0..10 scale.
 * These limits describe PHMS input support, not universal physiological limits or device accuracy.
 * Reference: https://www.fda.gov/medical-devices/in-vitro-diagnostics/blood-glucose-monitoring-devices
 */
object HealthValidation {
    data class Issue(val field: String, val message: String)
    fun vital(name: String, unit: String, value: Double?, secondary: Double?, needsSecondary: Boolean): Issue? {
        if (value == null || !value.isFinite()) return Issue("Reading", "Enter a finite number.")
        val key = name.lowercase(java.util.Locale.US)
        if (needsSecondary && (secondary == null || !secondary.isFinite()))
            return Issue("Diastolic", "Enter a finite diastolic reading.")
        if (secondary != null && !secondary.isFinite()) return Issue("Second reading", "Enter a finite number.")
        if (!needsSecondary && !key.contains("pressure") && secondary != null)
            return Issue("Second reading", "This measurement accepts one reading.")
        val expectedUnit = when {
            key.contains("pressure") -> "mmHg"
            key.contains("glucose") || key.contains("cholesterol") -> "mg/dL"
            key.contains("heart") || key.contains("pulse") -> "bpm"
            key.contains("oxygen") -> "%"
            else -> null
        }
        if (expectedUnit != null && unit != expectedUnit)
            return Issue("Unit", "Use $expectedUnit for this measurement.")
        if (key.contains("temperature") && unit !in listOf("°F", "°C"))
            return Issue("Unit", "Choose Fahrenheit or Celsius.")
        fun range(field: String, n: Double, low: Double, high: Double, units: String) =
            if (n !in low..high) Issue(field, "Enter $field between $low and $high $units.") else null
        return when {
            key.contains("pressure") -> range("Systolic", value, 1.0, 500.0, "mmHg")
                ?: range("Diastolic", secondary ?: return Issue("Diastolic", "Enter the diastolic reading."), 1.0, 500.0, "mmHg")
                ?: if (value <= secondary!!) Issue("Systolic", "Systolic must be higher than diastolic.") else null
            key.contains("pain") -> range("Pain score", value, 0.0, 10.0, "")
            key.contains("temperature") -> {
                val c = if (unit.contains("F", true)) (value - 32) * 5 / 9 else value
                if (c !in 10.0..50.0) Issue("Temperature", "Check the temperature and selected unit.") else null
            }
            key.contains("oxygen") -> range("Oxygen saturation", value, 0.0, 100.0, "%")
            key.contains("respiratory") -> range("Respiratory rate", value, 1.0, 300.0, "breaths/min")
            key.contains("heart") || key.contains("pulse") -> range("Heart rate", value, 1.0, 500.0, "bpm")
            key.contains("glucose") -> range("Glucose", value, 0.1, 2000.0, unit)
            key.contains("cholesterol") -> range("Cholesterol", value, 0.1, 3000.0, unit)
            value < 0 -> Issue("Reading", "Enter a non-negative reading.")
            else -> null
        }
    }
    fun requireVital(name: String, unit: String, value: Double, secondary: Double?, needsSecondary: Boolean) {
        vital(name, unit, value, secondary, needsSecondary)?.let { throw IllegalArgumentException("${it.field}: ${it.message}") }
    }
    fun name(value: String, label: String = "Name"): String? =
        if (value.isBlank() || value.trim().length > 160 || value.any { it.isISOControl() } || !value.any { it.isLetterOrDigit() })
            "$label must contain 1–160 readable characters." else null
    fun email(value: String, optional: Boolean = true): String? =
        if (optional && value.isBlank()) null else if (value.length <= 254 &&
            value.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) null else "Enter a valid email address."
    fun phone(value: String, optional: Boolean = true): String? =
        if (optional && value.isBlank()) null else if (value.count(Char::isDigit) in 7..15 &&
            value.matches(Regex("[+0-9 ().-]+")) && value.count { it == '+' } <= 1 && ('+' !in value || value.trim().startsWith("+"))) null else "Enter a valid phone number."
    fun weightKg(value: Double): String? =
        if (!value.isFinite() || value <= 0 || value > 1000) "Enter a weight greater than 0 and no more than 2,204.6 lb (1,000 kg)." else null
    fun heightCm(value: Double): String? =
        if (!value.isFinite() || value <= 0 || value > 300) "Enter a height greater than 0 and no more than 9 ft 10 in (300 cm)." else null
    fun parseBirthDate(value: String): Long? {
        val pos = ParsePosition(0)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value, pos)?.time
            ?.takeIf { pos.index == value.length }
    }
    fun birthDate(value: String, now: Long = System.currentTimeMillis()): String? {
        val date = parseBirthDate(value) ?: return "Choose a valid date of birth."
        return when {
            date > now -> "Date of birth cannot be in the future."
            age(value, now) > 130 -> "Please check your date of birth."
            else -> null
        }
    }
    fun age(value: String, now: Long = System.currentTimeMillis()): Int {
        val born = Calendar.getInstance().apply { timeInMillis = requireNotNull(parseBirthDate(value)) }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val years = today.get(Calendar.YEAR) - born.get(Calendar.YEAR)
        val beforeBirthday = today.get(Calendar.MONTH) < born.get(Calendar.MONTH) ||
            today.get(Calendar.MONTH) == born.get(Calendar.MONTH) && today.get(Calendar.DAY_OF_MONTH) < born.get(Calendar.DAY_OF_MONTH)
        return years - if (beforeBirthday) 1 else 0
    }
    fun medication(name: String, dosage: String, frequency: String, minutes: Int): String? =
        name(name, "Medication name")
            ?: if (dosage.isBlank() || dosage.length > 160 || dosage.any { it.isISOControl() } ||
                !dosage.any(Char::isDigit) || !dosage.any(Char::isLetter) || dosage.trim().startsWith("-") || dosage.contains("NaN", true) || dosage.contains("Infinity", true) ||
                Regex("[0-9]+(?:\\.[0-9]+)?").find(dosage)?.value?.toDoubleOrNull()?.let { it <= 0 } != false) "Enter a dose and unit, such as 5 mg or 1 tablet."
            else if (!frequency.trim().lowercase(Locale.US).matches(Regex("(?:daily|once(?: a day| daily)?|twice(?: a day| daily)?|[1-6] (?:times a day|times daily))"))) "Choose a medication frequency."
            else if (minutes !in 0..1439) "Choose a valid first dose time." else null
    fun calories(value: Int): String? = if (value !in 0..100000) "Calories must be between 0 and 100,000 kcal per item." else null
    fun grams(value: Double): String? = if (!value.isFinite() || value !in 0.0..100000.0) "Enter grams between 0 and 100,000." else null
}
