package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Monitoring System alert. [doseId] is unique so a missed dose raises at most one alert. */
@Entity(
    tableName = "Alert",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DoseLog::class, parentColumns = ["doseId"], childColumns = ["doseId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Medication::class, parentColumns = ["medicationId"], childColumns = ["medicationId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = VitalSign::class, parentColumns = ["vitalId"], childColumns = ["vitalId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [
        Index(value = ["userId", "acknowledgedAt", "createdAt"]),
        Index(value = ["doseId"], unique = true),
        Index("medicationId"),
        Index("vitalId")
    ]
)
data class Alert(
    @PrimaryKey(autoGenerate = true) val alertId: Long = 0,
    val userId: String,
    val type: AlertType,
    val message: String,
    val doseId: Long? = null,
    val medicationId: Long? = null,
    val vitalId: Long? = null,
    val createdAt: Long,
    val acknowledgedAt: Long? = null
)
