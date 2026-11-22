package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Medication change history: what (field), from (oldValue), to (newValue), when (changedAt). Written by SQL triggers. */
@Entity(
    tableName = "AuditLog",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Medication::class, parentColumns = ["medicationId"], childColumns = ["medicationId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("userId"), Index(value = ["medicationId", "changedAt"])]
)
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val auditId: Long = 0,
    val userId: String,
    val medicationId: Long,
    val field: String,
    val oldValue: String?,
    val newValue: String?,
    val changedAt: Long
)
