package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Dao
import androidx.room.Query
import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication

/**
 * Read-only cross-data search. Every query filters by userId on every table it reads.
 *
 * [pattern] is a LIKE pattern built by SearchRepository (keyword with %, _ and \ escaped, wrapped in %).
 * [from] and [to] are optional epoch-ms bounds; pass null to skip them.
 */
@Dao
abstract class SearchDao {

    @Query(
        """
        SELECT 'MEDICATION' AS kind, medicationId AS itemId, name AS title,
               dosage || ' · ' || frequency AS subtitle, startDate AS timestamp
        FROM Medication
        WHERE userId = :userId
          AND (name LIKE :pattern ESCAPE '\' OR dosage LIKE :pattern ESCAPE '\'
               OR frequency LIKE :pattern ESCAPE '\' OR instructions LIKE :pattern ESCAPE '\')
          AND (:from IS NULL OR startDate >= :from)
          AND (:to IS NULL OR startDate <= :to)
        ORDER BY startDate DESC
        """
    )
    abstract suspend fun searchMedications(userId: String, pattern: String, from: Long?, to: Long?): List<SearchResult>

    /** Doctors have no date, so they are left out when a date range is given. */
    @Query(
        """
        SELECT 'DOCTOR' AS kind, d.doctorId AS itemId, d.name AS title,
               COALESCE(s.name, '') AS subtitle, NULL AS timestamp
        FROM Doctor d LEFT JOIN Specialty s ON s.specialtyId = d.specialtyId
        WHERE d.userId = :userId
          AND (d.name LIKE :pattern ESCAPE '\' OR s.name LIKE :pattern ESCAPE '\')
          AND :from IS NULL AND :to IS NULL
        UNION ALL
        SELECT 'DOCTOR_VISIT' AS kind, v.visitId AS itemId, d.name AS title,
               CASE WHEN v.cancelled = 1 THEN 'Cancelled · ' || v.reason ELSE v.reason END AS subtitle, v.visitAt AS timestamp
        FROM DoctorVisit v JOIN Doctor d ON d.doctorId = v.doctorId AND d.userId = v.userId
        WHERE v.userId = :userId
          AND (d.name LIKE :pattern ESCAPE '\' OR v.reason LIKE :pattern ESCAPE '\' OR v.notes LIKE :pattern ESCAPE '\')
          AND (:from IS NULL OR v.visitAt >= :from)
          AND (:to IS NULL OR v.visitAt <= :to)
        ORDER BY timestamp DESC
        """
    )
    abstract suspend fun searchDoctorsAndVisits(userId: String, pattern: String, from: Long?, to: Long?): List<SearchResult>

    @Query(
        """
        SELECT 'VITAL_SIGN' AS kind, v.vitalId AS itemId, t.name AS title,
               CAST(v.value AS TEXT) || COALESCE('/' || CAST(v.secondaryValue AS TEXT), '') || ' ' || t.unit
                 || CASE WHEN v.isAbnormal = 1 THEN ' (abnormal)' ELSE '' END AS subtitle,
               v.recordedAt AS timestamp
        FROM VitalSign v JOIN VitalType t ON t.vitalTypeId = v.vitalTypeId
        WHERE v.userId = :userId
          AND (t.name LIKE :pattern ESCAPE '\' OR v.note LIKE :pattern ESCAPE '\')
          AND (:from IS NULL OR v.recordedAt >= :from)
          AND (:to IS NULL OR v.recordedAt <= :to)
        ORDER BY v.recordedAt DESC
        """
    )
    abstract suspend fun searchVitalSigns(userId: String, pattern: String, from: Long?, to: Long?): List<SearchResult>

    @Query(
        """
        SELECT 'DIET_ENTRY' AS kind, e.dietEntryId AS itemId, e.mealType AS title,
               CAST((SELECT COALESCE(SUM(f.calories), 0) FROM Food f
                     WHERE f.dietEntryId = e.dietEntryId AND f.userId = :userId) AS TEXT) || ' kcal' AS subtitle,
               e.eatenAt AS timestamp
        FROM DietEntry e
        WHERE e.userId = :userId
          AND (e.mealType LIKE :pattern ESCAPE '\' OR e.notes LIKE :pattern ESCAPE '\'
               OR EXISTS (SELECT 1 FROM Food f WHERE f.dietEntryId = e.dietEntryId AND f.userId = :userId
                          AND f.name LIKE :pattern ESCAPE '\'))
          AND (:from IS NULL OR e.eatenAt >= :from)
          AND (:to IS NULL OR e.eatenAt <= :to)
        UNION ALL
        SELECT 'WEIGHT' AS kind, w.weightId AS itemId, 'Weight' AS title,
               CAST(w.value AS TEXT) || ' ' || lower(w.unit) AS subtitle, w.recordedAt AS timestamp
        FROM Weight w
        WHERE w.userId = :userId
          AND ('weight' LIKE :pattern ESCAPE '\' OR CAST(w.value AS TEXT) LIKE :pattern ESCAPE '\')
          AND (:from IS NULL OR w.recordedAt >= :from)
          AND (:to IS NULL OR w.recordedAt <= :to)
        ORDER BY timestamp DESC
        """
    )
    abstract suspend fun searchDietAndWeight(userId: String, pattern: String, from: Long?, to: Long?): List<SearchResult>

    @Query(
        """
        SELECT 'NOTE' AS kind, noteId AS itemId, title, substr(body, 1, 80) AS subtitle, createdAt AS timestamp
        FROM Note
        WHERE userId = :userId
          AND (title LIKE :pattern ESCAPE '\' OR body LIKE :pattern ESCAPE '\')
          AND (:from IS NULL OR createdAt >= :from)
          AND (:to IS NULL OR createdAt <= :to)
        ORDER BY editedAt DESC
        """
    )
    abstract suspend fun searchNotes(userId: String, pattern: String, from: Long?, to: Long?): List<SearchResult>

    @Query(
        """
        SELECT 'DOSE_LOG' AS kind, d.doseId AS itemId, m.name AS title,
               m.dosage || ' · ' || d.status ||
               CASE WHEN d.recordedAt IS NOT NULL THEN ' · Recorded: ' || d.recordedAt ELSE '' END AS subtitle,
               d.scheduledAt AS timestamp
        FROM EffectiveDoseLog d JOIN Medication m ON m.medicationId = d.medicationId AND m.userId = d.userId
        WHERE d.userId = :userId
          AND (m.name LIKE :pattern ESCAPE '\' OR m.dosage LIKE :pattern ESCAPE '\'
               OR d.status LIKE :pattern ESCAPE '\')
          AND (:from IS NULL OR d.scheduledAt >= :from)
          AND (:to IS NULL OR d.scheduledAt <= :to)
        ORDER BY d.scheduledAt DESC
        """
    )
    abstract suspend fun searchIntakeHistory(userId: String, pattern: String, from: Long?, to: Long?): List<SearchResult>

    // ---- Shortcuts ----

    @Query(
        """
        SELECT v.*, d.name AS doctorName
        FROM DoctorVisit v JOIN Doctor d ON d.doctorId = v.doctorId AND d.userId = v.userId
        WHERE v.userId = :userId AND v.cancelled = 0 AND v.visitAt >= :now
        ORDER BY v.visitAt ASC LIMIT 1
        """
    )
    abstract suspend fun getNextDoctorVisit(userId: String, now: Long): DoctorVisitWithDoctor?

    @Query(
        """
        SELECT v.*, d.name AS doctorName
        FROM DoctorVisit v JOIN Doctor d ON d.doctorId = v.doctorId AND d.userId = v.userId
        WHERE v.userId = :userId AND v.cancelled = 0 AND v.visitAt < :now
        ORDER BY v.visitAt DESC LIMIT 1
        """
    )
    abstract suspend fun getLastDoctorVisit(userId: String, now: Long): DoctorVisitWithDoctor?

    /** Past medicines: deactivated, or past their end date. */
    @Query(
        """
        SELECT * FROM Medication
        WHERE userId = :userId AND (isActive = 0 OR (endDate IS NOT NULL AND endDate < :now))
        ORDER BY COALESCE(endDate, updatedAt) DESC
        """
    )
    abstract suspend fun getPastMedications(userId: String, now: Long): List<Medication>

    @Query("SELECT annualCheckupAt FROM Profile WHERE userId = :userId")
    abstract suspend fun getAnnualCheckupAt(userId: String): Long?
}
