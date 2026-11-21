package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Login account. [userId] is the login ID chosen at registration (at least 8 alphanumeric characters).
 * Passwords and security answers are stored only as salted PBKDF2 hashes (see security/PasswordHasher).
 */
@Entity(
    tableName = "User",
    indices = [Index(value = ["email"], unique = true)]
)
data class User(
    @PrimaryKey val userId: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    val securityQuestion: String,
    val securityAnswerHash: String,
    val securityAnswerSalt: String,
    val failedLoginAttempts: Int = 0,
    val lockedUntil: Long? = null,
    val createdAt: Long
)
