package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** User-entered immunization history; no inferred dose schedules or completion state. */
@Entity(tableName = "Vaccination",
    foreignKeys = [ForeignKey(entity = User::class, parentColumns = ["userId"],
        childColumns = ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")])
data class Vaccination(
    @PrimaryKey(autoGenerate = true) val vaccinationId: Long = 0,
    val userId: String,
    val name: String,
    val receivedDate: String,
    val doseNumber: Int? = null,
    val providerOrPharmacy: String = "",
    val location: String = "",
    val nextDoseDate: String? = null,
    val notes: String = "",
    val reminderEnabled: Boolean = false,
    val reminderNotifiedFor: String? = null
)
