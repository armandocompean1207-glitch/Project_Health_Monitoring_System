package com.cse3310.projecthealthmonitoringsystem.data.dao

/** Source values for one alert, joined by its vitalId, never by "latest reading". */
data class VitalAlertSource(
    val alertId: Long,
    val message: String,
    val vitalId: Long,
    val value: Double,
    val secondaryValue: Double?,
    val recordedAt: Long,
    val isAbnormal: Boolean,
    val note: String,
    val typeName: String,
    val unit: String
)
