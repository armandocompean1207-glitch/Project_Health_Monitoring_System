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
        ForeignKey(entity = Doctor::class, parentColumns = ["doctorId"], childColumns = ["doctorId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Pharmacy::class, parentColumns = ["pharmacyId"], childColumns = ["pharmacyId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["userId", "isActive"]), Index("doctorId"), Index("pharmacyId")]
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
    val updatedAt: Long,
    /** Comma-separated, unique minutes after local midnight. Null preserves the legacy schedule. */
    val doseTimesMinutes: String? = null,
    val pharmacyId: Long? = null,
    val prescriptionNumber: String? = null,
    val refillsRemaining: Int? = null,
    val nextRefillAt: Long? = null
)
