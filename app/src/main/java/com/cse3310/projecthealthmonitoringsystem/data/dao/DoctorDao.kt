package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cse3310.projecthealthmonitoringsystem.data.entity.Doctor
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import kotlinx.coroutines.flow.Flow

/** Doctor and DoctorVisit. Every read and write is scoped by userId. */
@Dao
abstract class DoctorDao {

    // ---- Doctor ----

    @Insert
    abstract suspend fun insertDoctor(doctor: Doctor): Long

    @Update
    protected abstract suspend fun updateDoctorRow(doctor: Doctor): Int

    /** Updates only if the row exists and belongs to [userId]. */
    @Transaction
    open suspend fun updateDoctor(userId: String, doctor: Doctor): Boolean =
        doctor.userId == userId && doctorOwned(userId, doctor.doctorId) && updateDoctorRow(doctor) > 0

    @Query("DELETE FROM Doctor WHERE doctorId = :doctorId AND userId = :userId")
    abstract suspend fun deleteDoctor(userId: String, doctorId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM Doctor WHERE doctorId = :doctorId AND userId = :userId)")
    abstract suspend fun doctorOwned(userId: String, doctorId: Long): Boolean

    @Query("SELECT * FROM Doctor WHERE doctorId = :doctorId AND userId = :userId")
    abstract suspend fun getDoctor(userId: String, doctorId: Long): Doctor?

    @Query(
        """
        SELECT d.*, s.name AS specialtyName
        FROM Doctor d LEFT JOIN Specialty s ON s.specialtyId = d.specialtyId
        WHERE d.userId = :userId
        ORDER BY d.name COLLATE NOCASE
        """
    )
    abstract fun observeDoctors(userId: String): Flow<List<DoctorWithSpecialty>>

    // ---- Pharmacies, maintained by the same care-team module ----

    @Insert
    abstract suspend fun insertPharmacy(pharmacy: com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy): Long

    @Update
    protected abstract suspend fun updatePharmacyRow(pharmacy: com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy): Int

    @Transaction
    open suspend fun updatePharmacy(userId: String, pharmacy: com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy): Boolean =
        pharmacy.userId == userId && getPharmacy(userId, pharmacy.pharmacyId) != null && updatePharmacyRow(pharmacy) > 0

    @Query("SELECT * FROM Pharmacy WHERE userId = :userId ORDER BY isPreferred DESC, name COLLATE NOCASE")
    abstract fun observePharmacies(userId: String): Flow<List<com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy>>

    @Query("SELECT * FROM Pharmacy WHERE userId = :userId AND pharmacyId = :pharmacyId")
    abstract suspend fun getPharmacy(userId: String, pharmacyId: Long): com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy?

    @Query("DELETE FROM Pharmacy WHERE userId = :userId AND pharmacyId = :pharmacyId")
    abstract suspend fun deletePharmacy(userId: String, pharmacyId: Long): Int

    @Query("UPDATE Pharmacy SET isPreferred = 1 WHERE userId = :userId AND pharmacyId = :pharmacyId")
    abstract suspend fun preferPharmacy(userId: String, pharmacyId: Long): Int

    // ---- DoctorVisit ----

    @Insert
    abstract suspend fun insertVisit(visit: DoctorVisit): Long

    @Update
    protected abstract suspend fun updateVisitRow(visit: DoctorVisit): Int

    @Transaction
    open suspend fun updateVisit(userId: String, visit: DoctorVisit): Boolean {
        if (visit.userId != userId || !doctorOwned(userId, visit.doctorId)) return false
        val original = getVisit(userId, visit.visitId) ?: return false
        val changed = visit.copy(reminderVersion = original.reminderVersion + 1,
            reminderSentMask = if (original.visitAt == visit.visitAt) original.reminderSentMask and visit.reminderMask else 0)
        return updateVisitRow(changed) > 0
    }

    @Query("""UPDATE DoctorVisit SET reminderSentMask = reminderSentMask | :bit
        WHERE userId=:owner AND visitId=:id AND reminderVersion=:version
        AND visitAt=:at AND cancelled=0 AND (reminderMask & :bit)!=0 AND (reminderSentMask & :bit)=0""")
    abstract suspend fun claimVisitReminder(owner: String, id: Long, version: Long, at: Long, bit: Int): Int

    @Query("DELETE FROM DoctorVisit WHERE visitId = :visitId AND userId = :userId")
    abstract suspend fun deleteVisit(userId: String, visitId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM DoctorVisit WHERE visitId = :visitId AND userId = :userId)")
    abstract suspend fun visitOwned(userId: String, visitId: Long): Boolean

    @Query("SELECT * FROM DoctorVisit WHERE visitId = :visitId AND userId = :userId")
    abstract suspend fun getVisit(userId: String, visitId: Long): DoctorVisit?

    @Query(
        """
        SELECT v.*, d.name AS doctorName
        FROM DoctorVisit v JOIN Doctor d ON d.doctorId = v.doctorId AND d.userId = v.userId
        WHERE v.userId = :userId
        ORDER BY v.visitAt DESC
        """
    )
    abstract fun observeVisits(userId: String): Flow<List<DoctorVisitWithDoctor>>
}
