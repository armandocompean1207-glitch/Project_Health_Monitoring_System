package com.cse3310.projecthealthmonitoringsystem.monitoring

enum class DoseStatus { SCHEDULED, TAKEN, MISSED }

enum class AlertType { MISSED_DOSE, MEDICATION_CONFLICT }

data class Designee(
    val id: Long,
    val name: String,
    val relationship: String,
    val phone: String,
    val email: String
)

data class DoseEvent(
    val id: Long,
    val medicationName: String,
    val scheduledAtMillis: Long,
    val graceMinutes: Int,
    val status: DoseStatus = DoseStatus.SCHEDULED,
    val takenAtMillis: Long? = null
)

data class AlertRecord(
    val id: Long,
    val type: AlertType,
    val message: String,
    val createdAtMillis: Long,
    val sentTo: String
)

data class ConflictWarning(
    val drugA: String,
    val drugB: String,
    val reason: String
)
