package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Doctor",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Specialty::class, parentColumns = ["specialtyId"], childColumns = ["specialtyId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("userId"), Index("specialtyId")]
)
data class Doctor(
    @PrimaryKey(autoGenerate = true) val doctorId: Long = 0,
    val userId: String,
    val name: String,
    val specialtyId: Long? = null,
    val phone: String = "",
    val email: String = "",
    val address: String = ""
)
