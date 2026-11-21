package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Trusted contact notified about missed doses and conflicts. */
@Entity(
    tableName = "Designee",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("userId")]
)
data class Designee(
    @PrimaryKey(autoGenerate = true) val designeeId: Long = 0,
    val userId: String,
    val name: String,
    val relationship: String = "",
    val phone: String = "",
    val email: String = "",
    val preferredMethod: ContactMethod
)
