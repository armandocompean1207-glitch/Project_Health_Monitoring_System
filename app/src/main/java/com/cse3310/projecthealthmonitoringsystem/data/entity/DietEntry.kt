package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One meal. Its foods are Food rows. */
@Entity(
    tableName = "DietEntry",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["userId", "eatenAt"])]
)
data class DietEntry(
    @PrimaryKey(autoGenerate = true) val dietEntryId: Long = 0,
    val userId: String,
    val mealType: String,
    val eatenAt: Long,
    val notes: String = ""
)
