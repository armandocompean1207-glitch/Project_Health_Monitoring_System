package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cse3310.projecthealthmonitoringsystem.data.entity.Profile
import com.cse3310.projecthealthmonitoringsystem.data.entity.User
import kotlinx.coroutines.flow.Flow

/** User and Profile. Every query is keyed by userId (the row's own primary key). */
@Dao
abstract class UserDao {

    // ---- User ----

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertUser(user: User)

    @Update
    abstract suspend fun updateUser(user: User): Int

    @Query("DELETE FROM User WHERE userId = :userId")
    abstract suspend fun deleteUser(userId: String): Int

    @Query("SELECT * FROM User WHERE userId = :userId")
    abstract suspend fun getUser(userId: String): User?

    /** Login lookup. [identifier] is either the User ID (exact) or the email (case-insensitive). */
    @Query("SELECT * FROM User WHERE userId = :identifier OR email = :identifier LIMIT 1")
    abstract suspend fun findByEmailOrUserId(identifier: String): User?

    @Query("SELECT EXISTS(SELECT 1 FROM User WHERE userId = :userId)")
    abstract suspend fun userIdExists(userId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM User WHERE email = :email)")
    abstract suspend fun emailExists(email: String): Boolean

    @Query("UPDATE User SET failedLoginAttempts = failedLoginAttempts + 1 WHERE userId = :userId")
    abstract suspend fun incrementFailedAttempts(userId: String): Int

    @Query("SELECT failedLoginAttempts FROM User WHERE userId = :userId")
    abstract suspend fun getFailedAttempts(userId: String): Int?

    @Query("UPDATE User SET lockedUntil = :lockedUntil, failedLoginAttempts = 0 WHERE userId = :userId")
    abstract suspend fun setLockedUntil(userId: String, lockedUntil: Long?): Int

    @Query("UPDATE User SET failedLoginAttempts = 0, lockedUntil = NULL WHERE userId = :userId")
    abstract suspend fun resetFailedAttempts(userId: String): Int

    @Query("UPDATE User SET passwordHash = :hash, passwordSalt = :salt, failedLoginAttempts = 0, lockedUntil = NULL WHERE userId = :userId")
    abstract suspend fun updatePassword(userId: String, hash: String, salt: String): Int

    // ---- Profile ----

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertProfile(profile: Profile)

    @Update
    abstract suspend fun updateProfile(profile: Profile): Int

    @Query("DELETE FROM Profile WHERE userId = :userId")
    abstract suspend fun deleteProfile(userId: String): Int

    @Query("SELECT * FROM Profile WHERE userId = :userId")
    abstract suspend fun getProfile(userId: String): Profile?

    @Query("SELECT * FROM Profile WHERE userId = :userId")
    abstract fun observeProfile(userId: String): Flow<Profile?>
}
