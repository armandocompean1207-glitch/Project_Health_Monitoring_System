package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cse3310.projecthealthmonitoringsystem.data.entity.Designee
import kotlinx.coroutines.flow.Flow

/** Designees (trusted contacts). Every query is scoped by userId. */
@Dao
abstract class DesigneeDao {

    @Insert
    abstract suspend fun insertDesignee(designee: Designee): Long

    @Update
    protected abstract suspend fun updateDesigneeRow(designee: Designee): Int

    @Transaction
    open suspend fun updateDesignee(userId: String, designee: Designee): Boolean =
        designee.userId == userId && designeeOwned(userId, designee.designeeId) && updateDesigneeRow(designee) > 0

    @Query("DELETE FROM Designee WHERE designeeId = :designeeId AND userId = :userId")
    abstract suspend fun deleteDesignee(userId: String, designeeId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM Designee WHERE designeeId = :designeeId AND userId = :userId)")
    abstract suspend fun designeeOwned(userId: String, designeeId: Long): Boolean

    @Query("SELECT * FROM Designee WHERE designeeId = :designeeId AND userId = :userId")
    abstract suspend fun getDesignee(userId: String, designeeId: Long): Designee?

    @Query("SELECT * FROM Designee WHERE userId = :userId ORDER BY name COLLATE NOCASE")
    abstract fun observeDesignees(userId: String): Flow<List<Designee>>

    /** For the alert hand-off: everyone to notify. */
    @Query("SELECT * FROM Designee WHERE userId = :userId ORDER BY name COLLATE NOCASE")
    abstract suspend fun getDesignees(userId: String): List<Designee>
}
