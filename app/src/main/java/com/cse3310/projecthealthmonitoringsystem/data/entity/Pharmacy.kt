package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.*

@Entity(tableName = "Pharmacy",
    foreignKeys = [ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("userId")])
data class Pharmacy(
    @PrimaryKey(autoGenerate = true) val pharmacyId: Long = 0,
    val userId: String,
    val name: String,
    val street: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val phone: String = "",
    val pharmacistName: String = "",
    val isPreferred: Boolean = false
) {
    fun directionsAddress(): String = listOf(street, city, listOf(state, zip).filter(String::isNotBlank).joinToString(" "))
        .filter(String::isNotBlank).joinToString(", ")
}
