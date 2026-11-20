package com.cse3310.projecthealthmonitoringsystem.monitoring

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MonitoringRepository {

    var externalMedicationNames: () -> List<String> = { emptyList() }

    private val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)

    fun formatTime(millis: Long): String = timeFormat.format(Date(millis))

    fun addDesignee(name: String, relationship: String, phone: String, email: String): Designee {
        val designee = Designee(
            id = MonitoringStore.nextId(),
            name = name.trim(),
            relationship = relationship.trim(),
            phone = phone.trim(),
            email = email.trim()
        )
        MonitoringStore.designees.add(designee)
        MonitoringStore.save()
        return designee
    }

    fun removeDesignee(id: Long) {
        MonitoringStore.designees.removeAll { it.id == id }
        MonitoringStore.save()
    }

    fun scheduleDose(
        context: Context,
        medicationName: String,
        scheduledAtMillis: Long,
        graceMinutes: Int = 30
    ): DoseEvent {
        val dose = DoseEvent(
            id = MonitoringStore.nextId(),
            medicationName = medicationName.trim(),
            scheduledAtMillis = scheduledAtMillis,
            graceMinutes = graceMinutes.coerceAtLeast(0)
        )
        MonitoringStore.doses.add(dose)
        MonitoringStore.save()
        MissedDoseScheduler.schedule(context, dose)
        return dose
    }

    fun markTaken(context: Context, doseId: Long) {
        val index = MonitoringStore.doses.indexOfFirst { it.id == doseId }
        if (index < 0) return
        val dose = MonitoringStore.doses[index]
        if (dose.status == DoseStatus.TAKEN) return
        MonitoringStore.doses[index] = dose.copy(
            status = DoseStatus.TAKEN,
            takenAtMillis = System.currentTimeMillis()
        )
        MonitoringStore.save()
        MissedDoseScheduler.cancel(context, doseId)
    }

    fun deleteDose(context: Context, doseId: Long) {
        MissedDoseScheduler.cancel(context, doseId)
        MonitoringStore.doses.removeAll { it.id == doseId }
        MonitoringStore.save()
    }

    fun onDoseTime(context: Context, doseId: Long) {
        val dose = MonitoringStore.doses.firstOrNull { it.id == doseId } ?: return
        if (dose.status != DoseStatus.SCHEDULED) return
        AlertSender.notifyUser(
            context,
            notificationId = (doseId * 2).toInt(),
            title = "Time to take ${dose.medicationName}",
            text = "Scheduled for ${formatTime(dose.scheduledAtMillis)}. Open PHMS and mark it as taken."
        )
    }

    fun onDoseDeadline(context: Context, doseId: Long): Boolean {
        val index = MonitoringStore.doses.indexOfFirst { it.id == doseId }
        if (index < 0) return false
        val dose = MonitoringStore.doses[index]
        if (dose.status != DoseStatus.SCHEDULED) return false

        MonitoringStore.doses[index] = dose.copy(status = DoseStatus.MISSED)

        val message = "PHMS alert: ${dose.medicationName} scheduled for " +
            "${formatTime(dose.scheduledAtMillis)} was not taken."
        AlertSender.notifyUser(
            context,
            notificationId = (doseId * 2 + 1).toInt(),
            title = "Missed dose: ${dose.medicationName}",
            text = message
        )
        val sentTo = AlertSender.alertDesignees(context, message)
        recordAlert(AlertType.MISSED_DOSE, message, sentTo)
        return true
    }

    fun currentMedicationNames(): List<String> =
        (externalMedicationNames() + MonitoringStore.doses.map { it.medicationName })
            .filter { it.isNotBlank() }
            .distinctBy { it.trim().lowercase() }

    fun checkConflicts(newMedication: String): List<ConflictWarning> =
        ConflictTable.findConflicts(newMedication, currentMedicationNames())

    fun reportConflicts(context: Context, newMedication: String, conflicts: List<ConflictWarning>) {
        if (conflicts.isEmpty()) return
        val details = conflicts.joinToString("; ") { "${it.drugA} + ${it.drugB}: ${it.reason}" }
        val message = "PHMS alert: adding $newMedication conflicts with current medication ($details)."
        AlertSender.notifyUser(
            context,
            notificationId = MonitoringStore.nextId().toInt(),
            title = "Medication conflict",
            text = message
        )
        val sentTo = AlertSender.alertDesignees(context, message)
        recordAlert(AlertType.MEDICATION_CONFLICT, message, sentTo)
    }

    private fun recordAlert(type: AlertType, message: String, sentTo: String) {
        MonitoringStore.alerts.add(
            0,
            AlertRecord(
                id = MonitoringStore.nextId(),
                type = type,
                message = message,
                createdAtMillis = System.currentTimeMillis(),
                sentTo = sentTo
            )
        )
        MonitoringStore.save()
    }
}
