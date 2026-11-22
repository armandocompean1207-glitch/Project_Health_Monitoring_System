package pushtest.navigation

import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.util.UUID

data class VitalSign(
    val name: String,
    val value: String,
    val unit: String,
    val notes: String
)

data class HealthNote(
    val id: Int,
    val title: String,
    val category: String,
    val body: String,
    val linkedItem: String = "Maintenance",
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

val noteCategories = listOf(
    "Doctor Appointment",
    "Medication",
    "Diet",
    "Vital Signs",
    "General"
)

data class DietFoodEntry(
    val id: Int,
    val name: String,
    val calories: Int,
    val protein: Int,
    val sugar: Int,
    val date: String,
    val time: String
)

data class FavoriteFood(
    val name: String,
    val calories: Int,
    val protein: Int,
    val sugar: Int
)

data class WeightEntry(
    val id: Int,
    val weight: Double,
    val unit: String,
    val date: String
)

data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val dosage: String,
    val dosageUnit: String,
    val frequency: String,
    val frequencyUnit: String,
    val intake: String,
    val startDate: LocalDate,
    val firstDoseTime: LocalTime,
    val endDate: LocalDate?,
    val instructions: String
)

enum class DoseStatus {
    UPCOMING,
    DUE,
    TAKEN,
    MISSED,
    ACKNOWLEDGED
}

data class MedicationDose(
    val doseId: String,
    val medicationId: String,
    val medicationName: String,
    val dosage: String,
    val dosageUnit: String,
    val scheduledTime: LocalDateTime,
    val status: DoseStatus,
    val takenTime: LocalDateTime? = null,
    val acknowledged: Boolean = false,
    val conflictType: String? = null
)

fun calculateNextDose(
    medication: Medication,
    previousDose: LocalDateTime
): LocalDateTime {

    val frequency = medication.frequency.toLongOrNull()
        ?: return previousDose

    return when (medication.frequencyUnit) {
        "Hours" -> previousDose.plusHours(frequency)
        "Days" -> previousDose.plusDays(frequency)
        "Weeks" -> previousDose.plusWeeks(frequency)
        "Months" -> previousDose.plusMonths(frequency)
        "Years" -> previousDose.plusYears(frequency)
        else -> previousDose
    }
}