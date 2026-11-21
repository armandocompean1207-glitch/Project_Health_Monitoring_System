package com.cse3310.projecthealthmonitoringsystem.data.repository

import com.cse3310.projecthealthmonitoringsystem.data.dao.AlertDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.MedicationConflict
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert
import com.cse3310.projecthealthmonitoringsystem.data.entity.AlertType
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import kotlinx.coroutines.flow.Flow

/**
 * Monitoring System (Diyanshu): the missed-dose rule and alert records.
 * Doses come from MedicationRepository, never from MedicationDao.
 */
class MonitoringRepository(
    private val alertDao: AlertDao,
    private val medicationRepository: MedicationRepository,
    private val tx: TransactionRunner,
    private val clock: Clock = SystemClock
) {

    companion object {
        /** Default missed-dose window: 30 minutes after the scheduled time. */
        const val DEFAULT_MISSED_WINDOW_MS = 30 * 60 * 1000L
    }

    /**
     * Missed-dose rule for every overdue dose of this user: marks each SCHEDULED dose whose window has
     * passed as MISSED and creates one MISSED_DOSE alert per dose, all in one transaction.
     * Returns the new alerts (for the hand-off to Communication).
     */
    suspend fun checkMissedDoses(
        userId: String,
        now: Long = clock(),
        windowMs: Long = DEFAULT_MISSED_WINDOW_MS
    ): List<Alert> = tx.run {
        val overdue = medicationRepository.getOverdueDoses(userId, now, windowMs)
        if (overdue.isEmpty()) return@run emptyList()
        medicationRepository.markDosesMissed(userId, overdue.map { it.dose.doseId }, now)
        val ids = alertDao.insertAlerts(overdue.map {
            Alert(
                userId = userId,
                type = AlertType.MISSED_DOSE,
                message = "Missed dose: ${it.medicationName} ${it.dosage}",
                doseId = it.dose.doseId,
                medicationId = it.dose.medicationId,
                createdAt = now
            )
        }).filter { it > 0 }
        if (ids.isEmpty()) emptyList() else alertDao.getAlerts(userId, ids)
    }

    /**
     * Missed-dose check for one dose, for the alarm that fires after its window.
     * Returns the new alert, or null if the dose was taken, is not overdue, or is not this user's.
     */
    suspend fun checkMissedDose(
        userId: String,
        doseId: Long,
        now: Long = clock(),
        windowMs: Long = DEFAULT_MISSED_WINDOW_MS
    ): Alert? = tx.run {
        val dose = medicationRepository.getDose(userId, doseId) ?: return@run null
        if (dose.status != DoseStatus.SCHEDULED || dose.scheduledAt > now - windowMs) return@run null
        if (medicationRepository.markDosesMissed(userId, listOf(doseId), now) == 0) return@run null
        val medication = medicationRepository.getMedication(userId, dose.medicationId)
        val message = if (medication != null) "Missed dose: ${medication.name} ${medication.dosage}" else "Missed dose"
        val alert = Alert(
            userId = userId,
            type = AlertType.MISSED_DOSE,
            message = message,
            doseId = doseId,
            medicationId = dose.medicationId,
            createdAt = now
        )
        val id = alertDao.insertAlert(alert)
        if (id > 0) alert.copy(alertId = id) else null
    }

    /** CONFLICT alerts for conflicting pairs (from MedicationRepository.detectConflicts). */
    suspend fun raiseConflictAlerts(userId: String, conflicts: List<MedicationConflict>): List<Long> {
        if (conflicts.isEmpty()) return emptyList()
        val now = clock()
        return alertDao.insertAlerts(conflicts.map {
            Alert(
                userId = userId,
                type = AlertType.CONFLICT,
                message = "${it.medicationNameA} and ${it.medicationNameB}: ${it.description}",
                medicationId = it.medicationIdB,
                createdAt = now
            )
        })
    }

    /** ABNORMAL_VITAL alert, raised by VitalsRepository when a reading is outside the normal range. */
    suspend fun raiseAbnormalVitalAlert(userId: String, vitalId: Long, message: String): Long =
        alertDao.insertAlert(
            Alert(userId = userId, type = AlertType.ABNORMAL_VITAL, message = message, vitalId = vitalId, createdAt = clock())
        )

    /** View Details */
    suspend fun getAlert(userId: String, alertId: Long): Alert? = alertDao.getAlert(userId, alertId)

    /** Acknowledge. Returns false if already acknowledged or not this user's. */
    suspend fun acknowledgeAlert(userId: String, alertId: Long): Boolean = alertDao.acknowledge(userId, alertId, clock()) > 0

    fun observeAlerts(userId: String): Flow<List<Alert>> = alertDao.observeAlerts(userId)

    fun observeUnacknowledgedAlerts(userId: String): Flow<List<Alert>> = alertDao.observeUnacknowledged(userId)

    suspend fun deleteAlert(userId: String, alertId: Long): Boolean = alertDao.deleteAlert(userId, alertId) > 0
}
