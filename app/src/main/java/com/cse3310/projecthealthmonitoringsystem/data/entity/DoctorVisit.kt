package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "DoctorVisit",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Doctor::class, parentColumns = ["doctorId"], childColumns = ["doctorId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["userId", "visitAt"]), Index("doctorId")]
)
data class DoctorVisit(
    @PrimaryKey(autoGenerate = true) val visitId: Long = 0,
    val userId: String,
    val doctorId: Long,
    val visitAt: Long,
    val reason: String = "",
    val notes: String = "",
    @ColumnInfo(defaultValue = "0") val reminderMask: Int = 10,
    @ColumnInfo(defaultValue = "0") val reminderSentMask: Int = 0,
    @ColumnInfo(defaultValue = "0") val reminderVersion: Long = 0,
    @ColumnInfo(defaultValue = "0") val cancelled: Boolean = false
)
