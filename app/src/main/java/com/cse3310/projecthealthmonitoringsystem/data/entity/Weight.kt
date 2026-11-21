package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Weight",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["userId", "recordedAt"])]
)
data class Weight(
    @PrimaryKey(autoGenerate = true) val weightId: Long = 0,
    val userId: String,
    val value: Double,
    val unit: WeightUnit,
    val recordedAt: Long
)
