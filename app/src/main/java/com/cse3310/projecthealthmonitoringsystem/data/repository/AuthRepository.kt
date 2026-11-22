package com.cse3310.projecthealthmonitoringsystem.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.cse3310.projecthealthmonitoringsystem.data.dao.UserDao
import com.cse3310.projecthealthmonitoringsystem.data.entity.Profile
import com.cse3310.projecthealthmonitoringsystem.data.entity.User
import kotlinx.coroutines.flow.Flow

/**
 * Registration and Login data (Nathan's auth/ module).
 *
 * This class never sees a plain-text password: security/PasswordHasher hashes it first and passes the
 * hash and salt in. The lockout rule lives here so it is enforced in one place.
 */
class AuthRepository(
    private val userDao: UserDao,
    private val tx: TransactionRunner,
    private val clock: Clock = SystemClock
) {

    companion object {
        const val MAX_FAILED_LOGINS = 3
        const val LOCKOUT_MS = 5 * 60 * 1000L
    }

    /** Lockout state after a failed login. [lockedUntil] is set once the limit is reached. */
    data class LoginLockState(val failedAttempts: Int, val lockedUntil: Long?)

    /** Creates the user and profile together. Returns false if the User ID or email is already taken. */
    suspend fun register(user: User, profile: Profile): Boolean {
        require(profile.userId == user.userId) { "Profile must belong to the new user" }
        val validation = com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
        (validation.name(profile.name) ?: validation.email(user.email, optional = false))?.let { throw IllegalArgumentException(it) }
        return try {
            tx.run {
                userDao.insertUser(user)
                userDao.insertProfile(profile)
            }
            true
        } catch (e: SQLiteConstraintException) {
            false
        }
    }

    suspend fun isUserIdTaken(userId: String): Boolean = userDao.userIdExists(userId)

    suspend fun isEmailTaken(email: String): Boolean = userDao.emailExists(email.trim())

    /** Login lookup by User ID or email. */
    suspend fun findAccount(identifier: String): User? = userDao.findByEmailOrUserId(identifier.trim())

    suspend fun getUser(userId: String): User? = userDao.getUser(userId)

    suspend fun isLocked(userId: String): Boolean {
        val until = userDao.getUser(userId)?.lockedUntil ?: return false
        return until > clock()
    }

    /** Counts a failed login. The third failure locks the account for [LOCKOUT_MS]. */
    suspend fun recordFailedLogin(userId: String): LoginLockState = tx.run {
        userDao.incrementFailedAttempts(userId)
        val attempts = userDao.getFailedAttempts(userId) ?: 0
        if (attempts >= MAX_FAILED_LOGINS) {
            val until = clock() + LOCKOUT_MS
            userDao.setLockedUntil(userId, until)
            LoginLockState(attempts, until)
        } else {
            LoginLockState(attempts, null)
        }
    }

    suspend fun recordSuccessfulLogin(userId: String) {
        userDao.resetFailedAttempts(userId)
    }

    /** Password reset after the security question was answered. Takes the new hash and salt, never the password. */
    suspend fun updatePassword(userId: String, passwordHash: String, passwordSalt: String): Boolean =
        userDao.updatePassword(userId, passwordHash, passwordSalt) > 0

    /**
     * Updates account fields such as email and security question. The password hash and salt and the
     * lockout counters are kept from the stored row, so this cannot be used to change the password
     * (use [updatePassword]) or to clear a lockout. Returns false for another user's row or a taken email.
     */
    suspend fun updateUser(userId: String, user: User): Boolean = tx.run {
        if (user.userId != userId) return@run false
        val stored = userDao.getUser(userId) ?: return@run false
        val safe = user.copy(
            passwordHash = stored.passwordHash,
            passwordSalt = stored.passwordSalt,
            failedLoginAttempts = stored.failedLoginAttempts,
            lockedUntil = stored.lockedUntil,
            createdAt = stored.createdAt
        )
        try {
            userDao.updateUser(safe) > 0
        } catch (e: SQLiteConstraintException) {
            false
        }
    }

    suspend fun getProfile(userId: String): Profile? = userDao.getProfile(userId)

    fun observeProfile(userId: String): Flow<Profile?> = userDao.observeProfile(userId)

    suspend fun updateProfile(userId: String, profile: Profile): Boolean {
        if (profile.userId != userId) return false
        val v = com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
        // Zero remains the legacy "not provided" value. Never reinterpret an existing stored unit.
        listOfNotNull(v.name(profile.name), v.phone(profile.phone),
            profile.dateOfBirth?.let { v.birthDate(it) },
            if (profile.heightCm != 0.0) v.heightCm(profile.heightCm) else null,
            if (profile.weight != 0.0) v.weightKg(DietRepository.convert(profile.weight, profile.weightUnit,
                com.cse3310.projecthealthmonitoringsystem.data.entity.WeightUnit.KG)) else null)
            .firstOrNull()?.let { throw IllegalArgumentException(it) }
        val updated = profile.dateOfBirth?.let { profile.copy(age = v.age(it)) } ?: profile
        return userDao.updateProfile(updated) > 0
    }
}
