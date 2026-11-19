package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cse3310.projecthealthmonitoringsystem.data.entity.CommunicationLog
import kotlinx.coroutines.flow.Flow

/** Delivery results for designee notifications. Every query is scoped by userId. */
@Dao
abstract class CommunicationLogDao {

    @Insert
    abstract suspend fun insertLog(log: CommunicationLog): Long

    @Update
    protected abstract suspend fun updateLogRow(log: CommunicationLog): Int

    @Transaction
    open suspend fun updateLog(userId: String, log: CommunicationLog): Boolean =
        log.userId == userId && logOwned(userId, log.commId) && updateLogRow(log) > 0

    @Query("DELETE FROM CommunicationLog WHERE commId = :commId AND userId = :userId")
    abstract suspend fun deleteLog(userId: String, commId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM CommunicationLog WHERE commId = :commId AND userId = :userId)")
    abstract suspend fun logOwned(userId: String, commId: Long): Boolean

    @Query("SELECT * FROM CommunicationLog WHERE userId = :userId ORDER BY sentAt DESC")
    abstract fun observeLogs(userId: String): Flow<List<CommunicationLog>>

    @Query("SELECT * FROM CommunicationLog WHERE userId = :userId AND alertId = :alertId ORDER BY sentAt")
    abstract suspend fun getLogsForAlert(userId: String, alertId: Long): List<CommunicationLog>

    /** For SMS retries: failed attempts so far for one alert and designee. */
    @Query(
        """
        SELECT COUNT(*) FROM CommunicationLog
        WHERE userId = :userId AND alertId = :alertId AND designeeId = :designeeId AND status = 'FAILED'
        """
    )
    abstract suspend fun countFailedAttempts(userId: String, alertId: Long, designeeId: Long): Int
}
