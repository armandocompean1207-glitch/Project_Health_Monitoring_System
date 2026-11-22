package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One scheduled dose. Append-only: status changes once from SCHEDULED to TAKEN or MISSED and the row can
 * never be deleted (SQLite triggers in DatabaseTriggers). The medication foreign key uses NO ACTION so a
 * medication with doses cannot be deleted.
 */
@Entity(
    tableName = "DoseLog",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.NO_ACTION),
        ForeignKey(entity = Medication::class, parentColumns = ["medicationId"], childColumns = ["medicationId"], onDelete = ForeignKey.NO_ACTION)
    ],
    indices = [
        Index(value = ["medicationId", "scheduledAt"], unique = true),
        Index(value = ["userId", "status", "scheduledAt"])
    ]
)
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val doseId: Long = 0,
    val userId: String,
    val medicationId: Long,
    val scheduledAt: Long,
    val status: DoseStatus = DoseStatus.SCHEDULED,
    /** When the dose was marked TAKEN or MISSED. */
    val recordedAt: Long? = null
)
