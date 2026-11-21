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

/**
 * Data Maintenance (Nathan): doctor and visit forms, and the editable lookup tables
 * (specialties, vital types with abnormal ranges, conflict pairs).
 * The medication form uses MedicationRepository and the designee form uses CommunicationRepository.
 */
class MaintenanceRepository(
    private val doctorDao: DoctorDao,
    private val lookupDao: LookupDao
) {

    // ---- Doctors ----

    /** Returns the new doctorId. */
    suspend fun addDoctor(userId: String, doctor: Doctor): Long {
        require(doctor.userId == userId) { "Doctor must belong to the logged-in user" }
        return doctorDao.insertDoctor(doctor)
    }

    suspend fun editDoctor(userId: String, doctor: Doctor): Boolean = doctorDao.updateDoctor(userId, doctor)

    /** Also deletes the doctor's visits; medications keep their data with no prescribing doctor. */
    suspend fun deleteDoctor(userId: String, doctorId: Long): Boolean = doctorDao.deleteDoctor(userId, doctorId) > 0

    suspend fun getDoctor(userId: String, doctorId: Long): Doctor? = doctorDao.getDoctor(userId, doctorId)

    fun observeDoctors(userId: String): Flow<List<DoctorWithSpecialty>> = doctorDao.observeDoctors(userId)

    // ---- Doctor visits ----

    /** Returns the new visitId, or null if the doctor is not this user's. */
    suspend fun addDoctorVisit(userId: String, visit: DoctorVisit): Long? {
        require(visit.userId == userId) { "Visit must belong to the logged-in user" }
        if (!doctorDao.doctorOwned(userId, visit.doctorId)) return null
        return doctorDao.insertVisit(visit)
    }

    suspend fun editDoctorVisit(userId: String, visit: DoctorVisit): Boolean = doctorDao.updateVisit(userId, visit)

    suspend fun deleteDoctorVisit(userId: String, visitId: Long): Boolean = doctorDao.deleteVisit(userId, visitId) > 0

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
        require(type.normalMin <= type.normalMax) { "Normal minimum must not exceed the maximum" }
        return lookupDao.insertVitalType(type.copy(name = type.name.trim())).takeIf { it > 0 }
    }

    suspend fun editVitalType(type: VitalType): Boolean {
        require(type.normalMin <= type.normalMax) { "Normal minimum must not exceed the maximum" }
        return runCatchingConstraint { lookupDao.updateVitalType(type.copy(name = type.name.trim())) > 0 }
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
