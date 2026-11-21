package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Lookup table of doctor specialties, shared by all accounts and editable in Data Maintenance. */
@Entity(tableName = "Specialty", indices = [Index(value = ["name"], unique = true)])
data class Specialty(
    @PrimaryKey(autoGenerate = true) val specialtyId: Long = 0,
    val name: String
)
