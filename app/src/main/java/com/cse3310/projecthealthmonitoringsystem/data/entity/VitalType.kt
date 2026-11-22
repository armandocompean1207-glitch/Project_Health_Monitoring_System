package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Lookup table of vital sign types with normal ranges, shared by all accounts and editable in Data Maintenance.
 * Two-number readings (blood pressure) use the secondary columns for the second number.
 * The ranges are for the class project and are not medical advice.
 */
@Entity(tableName = "VitalType", indices = [Index(value = ["name"], unique = true)])
data class VitalType(
    @PrimaryKey(autoGenerate = true) val vitalTypeId: Long = 0,
    val name: String,
    val unit: String,
    val normalMin: Double,
    val normalMax: Double,
    val secondaryLabel: String? = null,
    val secondaryMin: Double? = null,
    val secondaryMax: Double? = null
) {
    /** True when [value] (or [secondaryValue], for two-number types) is outside the normal range. */
    fun isAbnormal(value: Double, secondaryValue: Double? = null): Boolean {
        if (value < normalMin || value > normalMax) return true
        if (secondaryValue != null) {
            if (secondaryMin != null && secondaryValue < secondaryMin) return true
            if (secondaryMax != null && secondaryValue > secondaryMax) return true
        }
        return false
    }
}
