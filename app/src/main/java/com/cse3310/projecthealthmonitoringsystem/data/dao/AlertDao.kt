package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert
import kotlinx.coroutines.flow.Flow

/** Monitoring System alerts. Every query is scoped by userId. */
@Dao
abstract class AlertDao {

    @Query(SHAREABLE_ALERTS + " ORDER BY a.createdAt DESC, a.alertId DESC")
    abstract fun observeShareableAlerts(userId: String, now: Long, dayStart: Long, createdAfter: Long): Flow<List<Alert>>

    @Query(SHAREABLE_ALERTS + " AND a.alertId = :alertId")
    abstract suspend fun getShareableAlert(userId: String, alertId: Long, now: Long, dayStart: Long, createdAfter: Long): Alert?

    /** The ownership join prevents a linked reading from exposing another account's data. */
    @Query("""
        SELECT a.alertId, a.message, v.vitalId, v.value, v.secondaryValue, v.recordedAt,
               v.isAbnormal, v.note, t.name AS typeName, t.unit
        FROM Alert a JOIN VitalSign v ON a.vitalId = v.vitalId AND a.userId = v.userId
        JOIN VitalType t ON t.vitalTypeId = v.vitalTypeId
        WHERE a.userId = :userId AND a.type = 'ABNORMAL_VITAL'
    """)
    abstract suspend fun vitalAlertSources(userId: String): List<VitalAlertSource>

    @Query("""
        SELECT 0 AS alertId, '' AS message, v.vitalId, v.value, v.secondaryValue, v.recordedAt,
               v.isAbnormal, v.note, t.name AS typeName, t.unit
        FROM VitalSign v JOIN VitalType t ON t.vitalTypeId = v.vitalTypeId
        WHERE v.userId = :userId AND v.vitalId = :vitalId
    """)
    abstract suspend fun vitalAlertSource(userId: String, vitalId: Long): VitalAlertSource?

    @Query("SELECT * FROM Alert WHERE userId = :userId AND vitalId = :vitalId AND type = 'ABNORMAL_VITAL' ORDER BY alertId LIMIT 1")
    abstract suspend fun alertForVital(userId: String, vitalId: Long): Alert?

    @Query("""
        UPDATE Alert SET message = :message,
        acknowledgedAt = CASE WHEN :resolved THEN COALESCE(acknowledgedAt, :now) ELSE acknowledgedAt END
        WHERE alertId = :alertId AND userId = :userId AND type = 'ABNORMAL_VITAL'
    """)
    abstract suspend fun correctVitalAlert(userId: String, alertId: Long, message: String, resolved: Boolean, now: Long): Int

    /** Returns -1 if an alert for the same dose already exists (doseId is unique). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAlert(alert: Alert): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAlerts(alerts: List<Alert>): List<Long>

    @Update
    protected abstract suspend fun updateAlertRow(alert: Alert): Int

    @Transaction
    open suspend fun updateAlert(userId: String, alert: Alert): Boolean =
        alert.userId == userId && alertOwned(userId, alert.alertId) && updateAlertRow(alert) > 0

    @Query("DELETE FROM Alert WHERE alertId = :alertId AND userId = :userId")
    abstract suspend fun deleteAlert(userId: String, alertId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM Alert WHERE alertId = :alertId AND userId = :userId)")
    abstract suspend fun alertOwned(userId: String, alertId: Long): Boolean

    @Query("SELECT * FROM Alert WHERE alertId = :alertId AND userId = :userId")
    abstract suspend fun getAlert(userId: String, alertId: Long): Alert?

    @Query("SELECT * FROM Alert WHERE alertId IN (:alertIds) AND userId = :userId ORDER BY createdAt")
    abstract suspend fun getAlerts(userId: String, alertIds: List<Long>): List<Alert>

    @Query("SELECT * FROM Alert WHERE userId = :userId ORDER BY createdAt DESC")
    abstract fun observeAlerts(userId: String): Flow<List<Alert>>

    @Query("SELECT * FROM Alert WHERE userId = :userId AND acknowledgedAt IS NULL ORDER BY createdAt DESC")
    abstract fun observeUnacknowledged(userId: String): Flow<List<Alert>>

    @Query("UPDATE Alert SET acknowledgedAt = :at WHERE userId = :userId AND doseId = :doseId AND type = 'MISSED_DOSE' AND acknowledgedAt IS NULL")
    abstract suspend fun resolveRecordedDose(userId: String, doseId: Long, at: Long): Int

    @Query("UPDATE Alert SET acknowledgedAt = :at WHERE alertId = :alertId AND userId = :userId AND acknowledgedAt IS NULL")
    abstract suspend fun acknowledge(userId: String, alertId: Long, at: Long): Int
}
