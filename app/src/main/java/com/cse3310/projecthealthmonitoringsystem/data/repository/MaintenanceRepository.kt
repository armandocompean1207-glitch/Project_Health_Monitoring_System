package com.cse3310.projecthealthmonitoringsystem.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.cse3310.projecthealthmonitoringsystem.data.dao.DoctorDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.DoctorVisitWithDoctor
import com.cse3310.projecthealthmonitoringsystem.data.dao.DoctorWithSpecialty
import com.cse3310.projecthealthmonitoringsystem.data.dao.LookupDao
import com.cse3310.projecthealthmonitoringsystem.data.entity.ConflictRule
import com.cse3310.projecthealthmonitoringsystem.data.entity.Doctor
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.data.entity.Specialty
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Data Maintenance (Nathan): doctor and visit forms, and the editable lookup tables
 * (specialties, vital types with abnormal ranges, conflict pairs).
 * The medication form uses MedicationRepository and the designee form uses CommunicationRepository.
 */
class MaintenanceRepository(
    private val doctorDao: DoctorDao,
    private val lookupDao: LookupDao,
    private val onVisitChanged: suspend (String, Long) -> Unit = { _, _ -> }
) {

    // ---- Doctors ----

    /** Returns the new doctorId. */
    suspend fun addDoctor(userId: String, doctor: Doctor): Long {
        require(doctor.userId == userId) { "Doctor must belong to the logged-in user" }
        validateDoctor(doctor)
        return doctorDao.insertDoctor(doctor)
    }

    suspend fun editDoctor(userId: String, doctor: Doctor): Boolean {
        validateDoctor(doctor)
        return doctorDao.updateDoctor(userId, doctor)
    }
    private fun validateDoctor(doctor: Doctor) {
        val v = com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
        (v.name(doctor.name, "Provider name") ?: v.phone(doctor.phone) ?: v.email(doctor.email)
            ?: com.cse3310.projecthealthmonitoringsystem.validation.ProviderPharmacyValidation.optionalText(doctor.providerType, "provider type", 160)
            ?: com.cse3310.projecthealthmonitoringsystem.validation.ProviderPharmacyValidation.optionalText(doctor.clinicName, "clinic or hospital")
            ?: com.cse3310.projecthealthmonitoringsystem.validation.ProviderPharmacyValidation.optionalText(doctor.address, "address", 500))
            ?.let { throw IllegalArgumentException(it) }
    }
    private fun validateVisit(visit: DoctorVisit) {
        require(visit.visitAt in 1..4102444799999L) { "Choose a valid appointment date before 2100." }
        require(visit.reminderMask in 0..63) { "Choose supported appointment reminders." }
        require(visit.reason.length <= 500 && visit.notes.length <= 10000) { "Shorten the appointment description." }
    }

    /** Also deletes the doctor's visits; medications keep their data with no prescribing doctor. */
    suspend fun deleteDoctor(userId: String, doctorId: Long): Boolean {
        val visits = observeDoctorVisits(userId).first()
        val removed = doctorDao.deleteDoctor(userId, doctorId) > 0
        if (removed) visits.filter { it.visit.doctorId == doctorId }.forEach { onVisitChanged(userId, it.visit.visitId) }
        return removed
    }

    suspend fun getDoctor(userId: String, doctorId: Long): Doctor? = doctorDao.getDoctor(userId, doctorId)

    fun observeDoctors(userId: String): Flow<List<DoctorWithSpecialty>> = doctorDao.observeDoctors(userId)

    // ---- Pharmacies ----

    fun observePharmacies(userId: String) = doctorDao.observePharmacies(userId)
    suspend fun getPharmacy(userId: String, pharmacyId: Long) = doctorDao.getPharmacy(userId, pharmacyId)
    suspend fun addPharmacy(userId: String, pharmacy: com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy): Long {
        require(pharmacy.userId == userId) { "Pharmacy must belong to the signed-in account." }
        validatePharmacy(pharmacy)
        return doctorDao.insertPharmacy(pharmacy.copy(pharmacyId = 0))
    }
    suspend fun editPharmacy(userId: String, pharmacy: com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy): Boolean {
        validatePharmacy(pharmacy)
        return doctorDao.updatePharmacy(userId, pharmacy)
    }
    suspend fun deletePharmacy(userId: String, pharmacyId: Long) = doctorDao.deletePharmacy(userId, pharmacyId) > 0
    suspend fun setPreferredPharmacy(userId: String, pharmacyId: Long) = doctorDao.preferPharmacy(userId, pharmacyId) > 0
    private fun validatePharmacy(pharmacy: com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy) {
        com.cse3310.projecthealthmonitoringsystem.validation.ProviderPharmacyValidation.pharmacy(pharmacy)
            ?.let { throw IllegalArgumentException(it) }
    }

    // ---- Doctor visits ----

    /** Returns the new visitId, or null if the doctor is not this user's. */
    suspend fun addDoctorVisit(userId: String, visit: DoctorVisit): Long? {
        require(visit.userId == userId) { "Visit must belong to the logged-in user" }
        validateVisit(visit)
        if (!doctorDao.doctorOwned(userId, visit.doctorId)) return null
        val id = doctorDao.insertVisit(visit.copy(visitId = 0, reminderSentMask = 0, reminderVersion = 0))
        onVisitChanged(userId, id)
        return id
    }

    suspend fun editDoctorVisit(userId: String, visit: DoctorVisit): Boolean {
        validateVisit(visit)
        val changed = doctorDao.updateVisit(userId, visit)
        if (changed) onVisitChanged(userId, visit.visitId)
        return changed
    }

    suspend fun deleteDoctorVisit(userId: String, visitId: Long): Boolean {
        val deleted = doctorDao.deleteVisit(userId, visitId) > 0
        if (deleted) onVisitChanged(userId, visitId)
        return deleted
    }
    suspend fun cancelDoctorVisit(userId: String, visitId: Long): Boolean {
        val visit = doctorDao.getVisit(userId, visitId) ?: return false
        return editDoctorVisit(userId, visit.copy(cancelled = true))
    }
    suspend fun claimAppointmentReminder(owner: String, id: Long, version: Long, at: Long, bit: Int): Boolean =
        doctorDao.claimVisitReminder(owner, id, version, at, bit) > 0

    suspend fun getDoctorVisit(userId: String, visitId: Long): DoctorVisit? = doctorDao.getVisit(userId, visitId)

    fun observeDoctorVisits(userId: String): Flow<List<DoctorVisitWithDoctor>> = doctorDao.observeVisits(userId)

    // ---- Specialties ----

    fun observeSpecialties(): Flow<List<Specialty>> = lookupDao.observeSpecialties()

    /** Returns the new id, or null if the name already exists. */
    suspend fun addSpecialty(name: String): Long? =
        lookupDao.insertSpecialty(Specialty(name = name.trim())).takeIf { it > 0 }

    suspend fun editSpecialty(specialty: Specialty): Boolean = runCatchingConstraint {
        lookupDao.updateSpecialty(specialty.copy(name = specialty.name.trim())) > 0
    }

    suspend fun deleteSpecialty(specialtyId: Long): Boolean = lookupDao.deleteSpecialty(specialtyId) > 0

    // ---- Vital types ----

    fun observeVitalTypes(): Flow<List<VitalType>> = lookupDao.observeVitalTypes()

    /** Returns the new id, or null if the name already exists. */
    suspend fun addVitalType(type: VitalType): Long? {
        validateType(type)
        return lookupDao.insertVitalType(type.copy(name = type.name.trim())).takeIf { it > 0 }
    }

    suspend fun editVitalType(type: VitalType): Boolean {
        validateType(type)
        return runCatchingConstraint { lookupDao.updateVitalType(type.copy(name = type.name.trim())) > 0 }
    }

    private fun validateType(type: VitalType) {
        val v = com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
        v.name(type.name, "Measurement name")?.let { throw IllegalArgumentException(it) }
        // Empty units are valid for dimensionless measurements; symbols such as % are also valid.
        require(type.unit.length <= 40 && type.unit.none { it.isISOControl() }) { "Enter a readable measurement unit." }
        require(type.normalMin.isFinite() && type.normalMax.isFinite() && type.normalMin <= type.normalMax) {
            "Enter finite, ordered reference limits."
        }
        require((type.secondaryMin == null && type.secondaryMax == null) ||
            (type.secondaryMin != null && type.secondaryMax != null && type.secondaryMin.isFinite() &&
                type.secondaryMax.isFinite() && type.secondaryMin <= type.secondaryMax)) {
            "Enter both secondary reference limits in order."
        }
    }
    /** Returns false if readings still use this type. */
    suspend fun deleteVitalType(vitalTypeId: Long): Boolean =
        runCatchingConstraint { lookupDao.deleteVitalType(vitalTypeId) > 0 }

    // ---- Conflict rules ----

    fun observeConflictRules(): Flow<List<ConflictRule>> = lookupDao.observeConflictRules()

    /** Returns the new id, or null if the pair already exists (in either order). */
    suspend fun addConflictRule(drugA: String, drugB: String, severity: String, description: String): Long? =
        lookupDao.insertConflictRule(ConflictRule.of(drugA, drugB, severity, description)).takeIf { it > 0 }

    suspend fun editConflictRule(ruleId: Long, drugA: String, drugB: String, severity: String, description: String): Boolean =
        runCatchingConstraint {
            lookupDao.updateConflictRule(ConflictRule.of(drugA, drugB, severity, description, ruleId)) > 0
        }

    suspend fun deleteConflictRule(ruleId: Long): Boolean = lookupDao.deleteConflictRule(ruleId) > 0

    private inline fun runCatchingConstraint(block: () -> Boolean): Boolean =
        try {
            block()
        } catch (e: SQLiteConstraintException) {
            false
        }
}
