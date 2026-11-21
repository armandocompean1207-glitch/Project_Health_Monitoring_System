package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cse3310.projecthealthmonitoringsystem.data.entity.ConflictRule
import com.cse3310.projecthealthmonitoringsystem.data.entity.Specialty
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import kotlinx.coroutines.flow.Flow

/**
 * Editable lookup tables (Data Maintenance). These are reference data shared by every account on the
 * device, so they have no userId column. Inserts return -1 when the name or pair already exists.
 */
@Dao
abstract class LookupDao {

    // ---- Specialty ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSpecialty(specialty: Specialty): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun updateSpecialty(specialty: Specialty): Int

    @Query("DELETE FROM Specialty WHERE specialtyId = :specialtyId")
    abstract suspend fun deleteSpecialty(specialtyId: Long): Int

    @Query("SELECT * FROM Specialty ORDER BY name")
    abstract fun observeSpecialties(): Flow<List<Specialty>>

    @Query("SELECT * FROM Specialty ORDER BY name")
    abstract suspend fun getSpecialties(): List<Specialty>

    // ---- VitalType ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertVitalType(vitalType: VitalType): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun updateVitalType(vitalType: VitalType): Int

    /** Throws SQLiteConstraintException if a VitalSign still uses this type (RESTRICT). */
    @Query("DELETE FROM VitalType WHERE vitalTypeId = :vitalTypeId")
    abstract suspend fun deleteVitalType(vitalTypeId: Long): Int

    @Query("SELECT * FROM VitalType ORDER BY name")
    abstract fun observeVitalTypes(): Flow<List<VitalType>>

    @Query("SELECT * FROM VitalType ORDER BY name")
    abstract suspend fun getVitalTypes(): List<VitalType>

    // ---- ConflictRule ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertConflictRule(rule: ConflictRule): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun updateConflictRule(rule: ConflictRule): Int

    @Query("DELETE FROM ConflictRule WHERE ruleId = :ruleId")
    abstract suspend fun deleteConflictRule(ruleId: Long): Int

    @Query("SELECT * FROM ConflictRule ORDER BY drugA, drugB")
    abstract fun observeConflictRules(): Flow<List<ConflictRule>>

    @Query("SELECT * FROM ConflictRule ORDER BY drugA, drugB")
    abstract suspend fun getConflictRules(): List<ConflictRule>
}
