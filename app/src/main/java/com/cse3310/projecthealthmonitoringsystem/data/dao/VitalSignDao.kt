package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalSign
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import kotlinx.coroutines.flow.Flow

/** VitalSign (scoped by userId) plus read access to the shared VitalType lookup. */
@Dao
abstract class VitalSignDao {

    @Insert
    abstract suspend fun insertVitalSign(vital: VitalSign): Long

    @Update
    protected abstract suspend fun updateVitalSignRow(vital: VitalSign): Int

    @Transaction
    open suspend fun updateVitalSign(userId: String, vital: VitalSign): Boolean =
        vital.userId == userId && vitalOwned(userId, vital.vitalId) && updateVitalSignRow(vital) > 0

    @Query("DELETE FROM VitalSign WHERE vitalId = :vitalId AND userId = :userId")
    abstract suspend fun deleteVitalSign(userId: String, vitalId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM VitalSign WHERE vitalId = :vitalId AND userId = :userId)")
    abstract suspend fun vitalOwned(userId: String, vitalId: Long): Boolean

    @Query("""
        SELECT * FROM VitalSign WHERE userId = :userId AND vitalTypeId = :typeId
        AND value = :value AND secondaryValue IS :secondary AND recordedAt = :recordedAt AND note = :note
        LIMIT 1
    """)
    abstract suspend fun matchingSubmission(userId: String, typeId: Long, value: Double, secondary: Double?,
        recordedAt: Long, note: String): VitalSign?

    @Query("SELECT * FROM VitalSign WHERE vitalId = :vitalId AND userId = :userId")
    abstract suspend fun getVitalSign(userId: String, vitalId: Long): VitalSign?

    @Query(
        """
        SELECT v.*, t.name AS typeName, t.unit AS unit
        FROM VitalSign v JOIN VitalType t ON t.vitalTypeId = v.vitalTypeId
        WHERE v.userId = :userId
        ORDER BY v.recordedAt DESC
        """
    )
    abstract fun observeVitalSigns(userId: String): Flow<List<VitalSignWithType>>

    @Query(
        """
        SELECT v.*, t.name AS typeName, t.unit AS unit
        FROM VitalSign v JOIN VitalType t ON t.vitalTypeId = v.vitalTypeId
        WHERE v.userId = :userId AND v.vitalTypeId = :vitalTypeId AND v.recordedAt BETWEEN :from AND :to
        ORDER BY v.recordedAt DESC
        """
    )
    abstract fun observeByTypeAndRange(userId: String, vitalTypeId: Long, from: Long, to: Long): Flow<List<VitalSignWithType>>

    @Query(
        """
        SELECT v.*, t.name AS typeName, t.unit AS unit
        FROM VitalSign v JOIN VitalType t ON t.vitalTypeId = v.vitalTypeId
        WHERE v.userId = :userId AND v.isAbnormal = 1
        ORDER BY v.recordedAt DESC
        """
    )
    abstract fun observeAbnormal(userId: String): Flow<List<VitalSignWithType>>

    // ---- VitalType (read-only here; edited through LookupDao) ----

    @Query("SELECT * FROM VitalType WHERE vitalTypeId = :vitalTypeId")
    abstract suspend fun getVitalType(vitalTypeId: Long): VitalType?

    @Query("SELECT * FROM VitalType ORDER BY name")
    abstract fun observeVitalTypes(): Flow<List<VitalType>>
}
