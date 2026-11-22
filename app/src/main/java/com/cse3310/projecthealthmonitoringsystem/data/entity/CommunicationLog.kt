package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Delivery result of one attempt to notify a designee. */
@Entity(
    tableName = "CommunicationLog",
    foreignKeys = [
        ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Alert::class, parentColumns = ["alertId"], childColumns = ["alertId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = Designee::class, parentColumns = ["designeeId"], childColumns = ["designeeId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["userId", "sentAt"]), Index("alertId"), Index("designeeId")]
)
data class CommunicationLog(
    @PrimaryKey(autoGenerate = true) val commId: Long = 0,
    val userId: String,
    val alertId: Long? = null,
    val designeeId: Long? = null,
    val method: ContactMethod,
    val status: DeliveryStatus,
    val attempt: Int = 1,
    val detail: String = "",
    val sentAt: Long
)
