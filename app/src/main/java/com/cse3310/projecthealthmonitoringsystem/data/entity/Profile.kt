package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Personal details captured at registration. One row per user. */
@Entity(
    tableName = "Profile",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class Profile(
    @PrimaryKey val userId: String,
    val name: String,
    val phone: String,
    val gender: String,
    val age: Int,
    val weight: Double,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val heightCm: Double,
    /** Next annual checkup, epoch ms. */
    val annualCheckupAt: Long? = null,
    /** ISO local date, nullable for profiles created before version 2. */
    val dateOfBirth: String? = null
)
