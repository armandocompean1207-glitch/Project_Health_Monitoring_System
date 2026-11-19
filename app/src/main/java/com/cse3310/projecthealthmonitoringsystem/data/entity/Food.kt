package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Food",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = DietEntry::class, parentColumns = ["dietEntryId"], childColumns = ["dietEntryId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("userId"), Index("dietEntryId")]
)
data class Food(
    @PrimaryKey(autoGenerate = true) val foodId: Long = 0,
    val userId: String,
    val dietEntryId: Long,
    val name: String,
    val calories: Int,
    val proteinGrams: Double = 0.0,
    val sugarGrams: Double = 0.0
)
