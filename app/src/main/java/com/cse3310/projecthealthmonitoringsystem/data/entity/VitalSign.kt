package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "VitalSign",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = VitalType::class, parentColumns = ["vitalTypeId"], childColumns = ["vitalTypeId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index(value = ["userId", "vitalTypeId", "recordedAt"]), Index("vitalTypeId")]
)
data class VitalSign(
    @PrimaryKey(autoGenerate = true) val vitalId: Long = 0,
    val userId: String,
    val vitalTypeId: Long,
    val value: Double,
    /** Second number for two-part readings such as diastolic blood pressure. */
    val secondaryValue: Double? = null,
    val recordedAt: Long,
    val isAbnormal: Boolean = false,
    val note: String = ""
)
