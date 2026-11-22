package com.cse3310.projecthealthmonitoringsystem.data.repository

import com.cse3310.projecthealthmonitoringsystem.data.dao.VitalSignDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.VitalSignWithType
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalSign
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import kotlinx.coroutines.flow.Flow

/**
 * Vital Signs (Arpan). Sets the abnormal flag from the VitalType range and, for an abnormal reading,
 * raises an ABNORMAL_VITAL alert through MonitoringRepository in the same transaction.
 */
class VitalsRepository(
    private val vitalSignDao: VitalSignDao,
    private val monitoringRepository: MonitoringRepository,
    private val tx: TransactionRunner,
    private val clock: Clock = SystemClock
) {

    data class AddVitalResult(val vitalId: Long, val isAbnormal: Boolean, val alertId: Long?)

    fun observeVitalTypes(): Flow<List<VitalType>> = vitalSignDao.observeVitalTypes()

    /** Returns null if [vitalTypeId] does not exist. */
    suspend fun addVitalSign(
        userId: String,
        vitalTypeId: Long,
        value: Double,
        secondaryValue: Double? = null,
        recordedAt: Long = clock(),
        note: String = ""
    ): AddVitalResult? = tx.run {
        require(recordedAt in 1..4102444799999L) { "Choose a valid reading date." }
        val type = vitalSignDao.getVitalType(vitalTypeId) ?: return@run null
        validate(type, value, secondaryValue)
        val abnormal = type.isAbnormal(value, secondaryValue)
        val vitalId = vitalSignDao.insertVitalSign(
            VitalSign(
                userId = userId,
                vitalTypeId = vitalTypeId,
                value = value,
                secondaryValue = secondaryValue,
                recordedAt = recordedAt,
                isAbnormal = abnormal,
                note = note
            )
        )
        val alertId = if (abnormal) {
            monitoringRepository.raiseAbnormalVitalAlert(userId, vitalId, abnormalMessage(type, value, secondaryValue))
        } else null
        AddVitalResult(vitalId, abnormal, alertId)
    }

    /** Recomputes the abnormal flag. Editing does not raise a second alert. */
    suspend fun editVitalSign(userId: String, vital: VitalSign): Boolean = tx.run {
        require(vital.recordedAt in 1..4102444799999L) { "Choose a valid reading date." }
        val type = vitalSignDao.getVitalType(vital.vitalTypeId) ?: return@run false
        validate(type, vital.value, vital.secondaryValue)
        vitalSignDao.updateVitalSign(userId, vital.copy(isAbnormal = type.isAbnormal(vital.value, vital.secondaryValue)))
    }

    suspend fun deleteVitalSign(userId: String, vitalId: Long): Boolean = vitalSignDao.deleteVitalSign(userId, vitalId) > 0

    suspend fun getVitalSign(userId: String, vitalId: Long): VitalSign? = vitalSignDao.getVitalSign(userId, vitalId)

    fun observeVitalSigns(userId: String): Flow<List<VitalSignWithType>> = vitalSignDao.observeVitalSigns(userId)

    fun observeVitalSigns(userId: String, vitalTypeId: Long, from: Long, to: Long): Flow<List<VitalSignWithType>> =
        vitalSignDao.observeByTypeAndRange(userId, vitalTypeId, from, to)

    fun observeAbnormalVitalSigns(userId: String): Flow<List<VitalSignWithType>> = vitalSignDao.observeAbnormal(userId)

    private fun validate(type: VitalType, value: Double, secondaryValue: Double?) {
        com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation.requireVital(
            type.name, type.unit, value, secondaryValue, type.secondaryLabel != null)
    }

    private fun abnormalMessage(type: VitalType, value: Double, secondaryValue: Double?): String {
        val reading = if (secondaryValue != null) "${fmt(value)}/${fmt(secondaryValue)}" else fmt(value)
        return "Abnormal ${type.name}: $reading ${type.unit}"
    }

    private fun fmt(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
}
