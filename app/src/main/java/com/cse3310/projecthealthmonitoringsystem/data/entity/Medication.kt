package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A prescribed medication. Once it has DoseLog rows it is deactivated ([isActive] = false), never deleted.
 * Changes to [name], [dosage] or [intakeTimeMinutes] write AuditLog rows through SQL triggers.
 */
@Entity(
    tableName = "Medication",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Doctor::class, parentColumns = ["doctorId"], childColumns = ["doctorId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["userId", "isActive"]), Index("doctorId")]
)
data class Medication(
    @PrimaryKey(autoGenerate = true) val medicationId: Long = 0,
    val userId: String,
    val name: String,
    val dosage: String,
    val frequency: String,
    /** Time of day for the dose, in minutes after local midnight (0..1439). A time of day, not an instant. */
    val intakeTimeMinutes: Int,
    val startDate: Long,
    val endDate: Long? = null,
    /** Prescribing doctor. */
    val doctorId: Long? = null,
    val instructions: String = "",
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)
