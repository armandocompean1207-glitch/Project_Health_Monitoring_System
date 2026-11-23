package com.cse3310.projecthealthmonitoringsystem.data.repository

import com.cse3310.projecthealthmonitoringsystem.data.dao.CandidateConflict
import com.cse3310.projecthealthmonitoringsystem.data.dao.DoseWithMedication
import com.cse3310.projecthealthmonitoringsystem.data.dao.MedicationConflict
import com.cse3310.projecthealthmonitoringsystem.data.dao.MedicationDao
import com.cse3310.projecthealthmonitoringsystem.data.entity.AuditLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.ConflictRule
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import kotlinx.coroutines.flow.Flow

/**
 * Medication (Diyanshu): medications, the dose log and the conflict check.
 * Monitoring asks this class for doses; it never uses MedicationDao directly.
 */
class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val tx: TransactionRunner,
    private val clock: Clock = SystemClock
) {

    enum class DeleteResult { DELETED, DEACTIVATED, NOT_FOUND }

    // ---- Medications ----

    /** AddMedication. Returns the new medicationId, or null if the prescribing doctor is not this user's. */
    suspend fun addMedication(userId: String, medication: Medication): Long? {
        require(medication.userId == userId) { "Medication must belong to the logged-in user" }
        validate(medication)
        val doctorId = medication.doctorId
        if (doctorId != null && !medicationDao.doctorOwned(userId, doctorId)) return null
        if (medication.pharmacyId?.let { !medicationDao.pharmacyOwned(userId, it) } == true) return null
        val now = clock()
        return medicationDao.insertMedication(medication.copy(medicationId = 0, createdAt = now, updatedAt = now))
    }

    /** EditMedication. Name, dosage and intake time changes are written to AuditLog by triggers. */
    suspend fun editMedication(userId: String, medication: Medication): Boolean {
        validate(medication)
        val doctorId = medication.doctorId
        if (doctorId != null && !medicationDao.doctorOwned(userId, doctorId)) return false
        if (medication.pharmacyId?.let { !medicationDao.pharmacyOwned(userId, it) } == true) return false
        return medicationDao.updateMedication(userId, medication.copy(updatedAt = clock()))
    }

    /** DeleteMedication: deletes a medication with no doses, deactivates one that has doses. */
    suspend fun deleteMedication(userId: String, medicationId: Long): DeleteResult = tx.run {
        when {
            !medicationDao.medicationOwned(userId, medicationId) -> DeleteResult.NOT_FOUND
            medicationDao.doseCount(userId, medicationId) > 0 -> {
                medicationDao.deactivateMedication(userId, medicationId, clock())
                DeleteResult.DEACTIVATED
            }
            else -> {
                medicationDao.deleteMedication(userId, medicationId)
                DeleteResult.DELETED
            }
        }
    }

    /** Stops future reminders while retaining the prescription and every dose record. */
    suspend fun deactivateMedication(userId: String, medicationId: Long): Boolean =
        medicationDao.deactivateMedication(userId, medicationId, clock()) > 0

    suspend fun getMedication(userId: String, medicationId: Long): Medication? =
        medicationDao.getMedication(userId, medicationId)

    fun observeActiveMedications(userId: String): Flow<List<Medication>> = medicationDao.observeActiveMedications(userId)

    fun observeAllMedications(userId: String): Flow<List<Medication>> = medicationDao.observeAllMedications(userId)

    /** For rescheduling alarms after reboot or edit. */
    suspend fun getActiveMedications(userId: String): List<Medication> = medicationDao.getActiveMedications(userId)

    fun observeAuditLog(userId: String, medicationId: Long): Flow<List<AuditLog>> =
        medicationDao.observeAuditLog(userId, medicationId)

    suspend fun getAuditLog(userId: String, medicationId: Long): List<AuditLog> =
        medicationDao.getAuditLog(userId, medicationId)

    // ---- Doses ----

    /**
     * Creates the SCHEDULED dose row for [scheduledAt] (call this when the reminder fires, not weeks ahead:
     * dose rows can never be deleted). Idempotent: returns the existing doseId if the row is already there.
     * Returns null if the medication is not this user's or is inactive.
     */
    suspend fun scheduleDose(userId: String, medicationId: Long, scheduledAt: Long): Long? = tx.run {
        val medication = medicationDao.getMedication(userId, medicationId)
        if (medication == null || !medication.isActive) return@run null
        if (scheduledAt < medication.startDate || medication.endDate?.let { scheduledAt > it } == true) return@run null
        if (medication.doseTimesMinutes != null &&
            !com.cse3310.projecthealthmonitoringsystem.medication.isScheduledIntake(medication, scheduledAt)) return@run null
        val id = medicationDao.insertDoseIfAbsent(
            DoseLog(userId = userId, medicationId = medicationId, scheduledAt = scheduledAt)
        )
        if (id > 0) id else medicationDao.getDoseAt(userId, medicationId, scheduledAt)?.doseId
    }

    suspend fun getDose(userId: String, doseId: Long): DoseLog? = medicationDao.getDose(userId, doseId)

    /** Account-scoped lookup of a specific scheduled occurrence. */
    suspend fun getDoseAt(userId: String, medicationId: Long, scheduledAt: Long): DoseLog? =
        medicationDao.getDoseAt(userId, medicationId, scheduledAt)

    /** Used by MonitoringRepository's atomic confirmation with its sampled confirmation clock. */
    internal suspend fun markDoseTakenAt(userId: String, doseId: Long, recordedAt: Long): Boolean =
        transitionScheduledDose(userId, doseId, DoseStatus.TAKEN, recordedAt)

    /** Mark as taken. Returns false if the dose is not this user's or is already TAKEN or MISSED. */
    suspend fun markDoseTaken(userId: String, doseId: Long): Boolean =
        transitionScheduledDose(userId, doseId, DoseStatus.TAKEN, clock())

    suspend fun markDoseMissed(userId: String, doseId: Long): Boolean =
        transitionScheduledDose(userId, doseId, DoseStatus.MISSED, clock())

    fun observeDoses(userId: String, medicationId: Long, from: Long, to: Long): Flow<List<DoseLog>> =
        medicationDao.observeDosesForMedication(userId, medicationId, from, to)

    fun observeDoseLog(userId: String, from: Long, to: Long): Flow<List<DoseWithMedication>> =
        medicationDao.observeDoseLog(userId, from, to)

    /** SCHEDULED doses whose missed window has passed. */
    suspend fun getOverdueDoses(userId: String, now: Long, windowMs: Long): List<DoseWithMedication> =
        medicationDao.getOverdueScheduledDoses(userId, now, windowMs)

    /** Marks the given doses MISSED in one statement. Already-final doses are skipped. */
    suspend fun markDosesMissed(userId: String, doseIds: List<Long>, at: Long): Int = tx.run {
        var changed = 0
        for (id in doseIds.distinct()) if (transitionScheduledDose(userId, id, DoseStatus.MISSED, at)) changed++
        changed
    }

    private suspend fun transitionScheduledDose(userId: String, doseId: Long, status: DoseStatus, at: Long): Boolean = tx.run {
        val effective = medicationDao.getDose(userId, doseId) ?: return@run false
        if (effective.status != DoseStatus.SCHEDULED) return@run false
        val original = medicationDao.originalDose(userId, doseId) ?: return@run false
        if (original.status == DoseStatus.SCHEDULED && medicationDao.latestCorrection(userId, doseId) == null)
            medicationDao.markDose(userId, doseId, status, at) > 0
        else medicationDao.insertCorrection(com.cse3310.projecthealthmonitoringsystem.data.entity.DoseCorrection(
            userId = userId, doseId = doseId, status = status,
            actualTakenAt = if (status == DoseStatus.TAKEN) at else null, changedAt = at,
            reason = if (status == DoseStatus.MISSED) "Missed-dose window after an intake correction" else "Patient confirmed intake"
        )) > 0
    }

    fun observeIntakeCorrections(userId: String, from: Long, to: Long) = medicationDao.observeCorrections(userId, from, to)
    suspend fun intakeCorrectionHistory(userId: String, doseId: Long) = medicationDao.correctionHistory(userId, doseId)
    suspend fun originalDose(userId: String, doseId: Long) = medicationDao.originalDose(userId, doseId)

    /** Explicit recording/correction with optimistic revision checking and idempotent retries. */
    suspend fun recordIntake(userId: String, medicationId: Long, scheduledAt: Long, status: DoseStatus,
        actualTakenAt: Long?, reason: String, expectedRevision: Long): Long = tx.run {
        require(status in listOf(DoseStatus.TAKEN, DoseStatus.SKIPPED, DoseStatus.SCHEDULED)) { "Choose a valid intake outcome." }
        val at = clock()
        require(reason.trim().isNotEmpty() && reason.length <= 500) { "Explain the entry or correction." }
        require(if (status == DoseStatus.TAKEN) actualTakenAt != null && actualTakenAt in 1..at else actualTakenAt == null) {
            "Choose the actual intake date and time, not a future time."
        }
        val med = requireNotNull(medicationDao.getMedication(userId, medicationId)) { "Medication is unavailable." }
        val current = medicationDao.getDoseAt(userId, medicationId, scheduledAt)
        val id = current?.doseId ?: run {
            require(com.cse3310.projecthealthmonitoringsystem.medication.isScheduledIntake(med, scheduledAt)) {
                "This time is not in the medication schedule."
            }
            requireNotNull(scheduleDose(userId, medicationId, scheduledAt)) { "This occurrence is unavailable." }
        }
        val last = medicationDao.latestCorrection(userId, id)
        if (last?.status == status && last.actualTakenAt == actualTakenAt) return@run id
        check((last?.correctionId ?: 0L) == expectedRevision) { "This intake record changed. Review it before saving." }
        // No duplicate append for an unchanged original entry, either.
        if (last == null && current?.status == status &&
            (status != DoseStatus.TAKEN || current.recordedAt == actualTakenAt)) return@run id
        medicationDao.insertCorrection(com.cse3310.projecthealthmonitoringsystem.data.entity.DoseCorrection(
            userId = userId, doseId = id, status = status, actualTakenAt = actualTakenAt, changedAt = at, reason = reason.trim()))
        id
    }

    // ---- Conflicts ----

    /** DetectConflict: active medications that conflict with [medicationName] (e.g. before adding it). */
    suspend fun detectConflict(userId: String, medicationName: String, excludeMedicationId: Long = 0): List<CandidateConflict> =
        medicationDao.getConflictsForCandidate(userId, ConflictRule.normalize(medicationName), excludeMedicationId)

    /** Every conflicting pair among the user's active medications. */
    suspend fun detectConflicts(userId: String): List<MedicationConflict> = medicationDao.getConflictsAmongActive(userId)

    private fun validate(medication: Medication) {
        com.cse3310.projecthealthmonitoringsystem.validation.ProviderPharmacyValidation.prescription(
            medication.prescriptionNumber, medication.refillsRemaining, medication.nextRefillAt)
            ?.let { throw IllegalArgumentException(it) }
        com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation.medication(
            medication.name, medication.dosage, medication.frequency, medication.intakeTimeMinutes
        )?.let { throw IllegalArgumentException(it) }
        medication.doseTimesMinutes?.let { encoded ->
            val times = com.cse3310.projecthealthmonitoringsystem.medication.MedicationTimes.decode(encoded)
            require(times != null && times.size == com.cse3310.projecthealthmonitoringsystem.medication.Frequency.timesPerDay(medication.frequency)) {
                "Choose a different valid time for every scheduled dose."
            }
            require(medication.intakeTimeMinutes == times.first()) { "First dose time must match the selected dose times." }
        }
        val end = medication.endDate
        require(end == null || end >= medication.startDate) { "End date must not be before the start date" }
    }
}
