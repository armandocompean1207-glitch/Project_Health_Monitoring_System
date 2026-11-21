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

    @Query("UPDATE Alert SET acknowledgedAt = :at WHERE alertId = :alertId AND userId = :userId AND acknowledgedAt IS NULL")
    abstract suspend fun acknowledge(userId: String, alertId: Long, at: Long): Int
}
