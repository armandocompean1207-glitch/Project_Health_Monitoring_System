package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Private note, visible only to its owner. */
@Entity(
    tableName = "Note",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["userId", "editedAt"])]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val noteId: Long = 0,
    val userId: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val editedAt: Long
)
