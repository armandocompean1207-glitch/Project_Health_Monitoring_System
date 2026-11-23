package com.cse3310.projecthealthmonitoringsystem.data.repository

import com.cse3310.projecthealthmonitoringsystem.data.dao.AlertDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.MedicationConflict
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert
import com.cse3310.projecthealthmonitoringsystem.data.entity.AlertType
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import com.cse3310.projecthealthmonitoringsystem.validation.VitalAssessment

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

    enum class IntakeOutcome { TAKEN, ALREADY_TAKEN, TOO_EARLY, MISSED, NOT_RECORDED, UNAVAILABLE }
    data class IntakeConfirmation(val outcome: IntakeOutcome, val doseId: Long? = null,
        val recordedAt: Long? = null, val alert: Alert? = null)

    /**
     * Explicit user confirmation only. Shares the missed-dose transaction so a final MISSED/TAKEN
     * status cannot be overwritten by a competing alarm or repeated tap. Records the actual clock,
     * not the scheduled time. Side effects such as alert delivery happen after this transaction.
     */
    suspend fun confirmMedicationIntake(userId: String, medicationId: Long, scheduledAt: Long): IntakeConfirmation = tx.run {
        val medication = medicationRepository.getMedication(userId, medicationId)
            ?: return@run IntakeConfirmation(IntakeOutcome.UNAVAILABLE)
        val existing = medicationRepository.getDoseAt(userId, medicationId, scheduledAt)
        if (existing?.status == DoseStatus.TAKEN)
            return@run IntakeConfirmation(IntakeOutcome.ALREADY_TAKEN, existing.doseId, existing.recordedAt)
        if (existing?.status == DoseStatus.MISSED || existing?.status == DoseStatus.SKIPPED)
            return@run IntakeConfirmation(IntakeOutcome.MISSED, existing.doseId, existing.recordedAt)
        val confirmedAt = clock()
        if (confirmedAt < scheduledAt) return@run IntakeConfirmation(IntakeOutcome.TOO_EARLY)
        if (existing == null) {
            // Preserve the existing policy: do not invent a past intake record or an alert when
            // the reminder never created a dose and its missed window has already elapsed.
            if (scheduledAt <= confirmedAt - DEFAULT_MISSED_WINDOW_MS)
                return@run IntakeConfirmation(IntakeOutcome.NOT_RECORDED)
            if (!com.cse3310.projecthealthmonitoringsystem.medication.isScheduledIntake(medication, scheduledAt))
                return@run IntakeConfirmation(IntakeOutcome.UNAVAILABLE)
        }
        val doseId = existing?.doseId ?: medicationRepository.scheduleDose(userId, medicationId, scheduledAt)
            ?: return@run IntakeConfirmation(IntakeOutcome.UNAVAILABLE)
        val missed = checkMissedDose(userId, doseId, confirmedAt)
        if (missed != null) return@run IntakeConfirmation(IntakeOutcome.MISSED, doseId, confirmedAt, missed)
        if (medicationRepository.markDoseTakenAt(userId, doseId, confirmedAt))
            IntakeConfirmation(IntakeOutcome.TAKEN, doseId, confirmedAt)
        else {
            val final = medicationRepository.getDose(userId, doseId)
            IntakeConfirmation(if (final?.status == DoseStatus.TAKEN) IntakeOutcome.ALREADY_TAKEN
                else if (final?.status == DoseStatus.MISSED) IntakeOutcome.MISSED else IntakeOutcome.UNAVAILABLE,
                doseId, final?.recordedAt)
        }
    }

    /** User-managed outcome/correction; any resolved missed alert remains in history. */
    suspend fun recordIntake(userId: String, medicationId: Long, scheduledAt: Long, status: DoseStatus,
        actualTakenAt: Long?, reason: String, expectedRevision: Long): Long = tx.run {
        val id = medicationRepository.recordIntake(userId, medicationId, scheduledAt, status, actualTakenAt, reason, expectedRevision)
        if (status == DoseStatus.TAKEN || status == DoseStatus.SKIPPED)
            alertDao.resolveRecordedDose(userId, id, clock())
        id
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

    /** Source-linked and idempotent, including concurrent retries inside the Room transaction. */
    suspend fun raiseAbnormalVitalAlert(userId: String, vitalId: Long): Long = tx.run {
        val source = requireNotNull(alertDao.vitalAlertSource(userId, vitalId)) { "Reading is unavailable." }
        require(source.isAbnormal) { "This reading does not need an alert." }
        val existing = alertDao.alertForVital(userId, vitalId)
        val message = source.messageFromReading()
        if (existing != null) {
            if (existing.message != message) alertDao.correctVitalAlert(userId, existing.alertId, message, false, clock())
            existing.alertId
        } else alertDao.insertAlert(
            Alert(userId = userId, type = AlertType.ABNORMAL_VITAL, message = message, vitalId = vitalId, createdAt = clock())
        )
    }

    /**
     * Repairs only source-linked, generated alert text. Never guesses an entered value, changes a
     * measurement or reinterprets historical ranges. A corrected normal reading resolves its stale
     * alert in history; legitimate abnormal records, alert IDs and communication logs are retained.
     */
    suspend fun repairVitalAlerts(userId: String): Int = tx.run {
        var corrected = 0
        for (source in alertDao.vitalAlertSources(userId)) {
            if (!source.message.startsWith("Abnormal ") && !source.message.startsWith("Corrected reading · ")) continue
            val expected = source.messageFromReading()
            if (source.message != expected)
                corrected += alertDao.correctVitalAlert(userId, source.alertId, expected, !source.isAbnormal, clock())
        }
        corrected
    }

    private fun com.cse3310.projecthealthmonitoringsystem.data.dao.VitalAlertSource.messageFromReading() =
        VitalAssessment.alertMessage(typeName, unit, value, secondaryValue, recordedAt, note, isAbnormal)

    /** View Details */
    suspend fun getAlert(userId: String, alertId: Long): Alert? {
        repairVitalAlerts(userId)
        return alertDao.getAlert(userId, alertId)
    }

    private fun dayStart(at: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = at
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Read-only sharing view: original active/history lists and acknowledgment rules are unchanged. */
    fun observeShareableAlerts(userId: String, now: Long = clock(), createdAfter: Long = 0): Flow<List<Alert>> =
        alertDao.observeShareableAlerts(userId, now, dayStart(now), createdAfter).onStart { repairVitalAlerts(userId) }

    /** Rechecks ownership, resolution and supersession immediately before preparing a message. */
    suspend fun getShareableAlert(userId: String, alertId: Long, now: Long = clock(), createdAfter: Long = 0): Alert? {
        repairVitalAlerts(userId)
        return alertDao.getShareableAlert(userId, alertId, now, dayStart(now), createdAfter)
    }

    /** Acknowledge. Returns false if already acknowledged or not this user's. */
    suspend fun acknowledgeAlert(userId: String, alertId: Long): Boolean = alertDao.acknowledge(userId, alertId, clock()) > 0

    fun observeAlerts(userId: String): Flow<List<Alert>> = alertDao.observeAlerts(userId).onStart { repairVitalAlerts(userId) }

    fun observeUnacknowledgedAlerts(userId: String): Flow<List<Alert>> = alertDao.observeUnacknowledged(userId).onStart { repairVitalAlerts(userId) }

    suspend fun deleteAlert(userId: String, alertId: Long): Boolean = alertDao.deleteAlert(userId, alertId) > 0
}
