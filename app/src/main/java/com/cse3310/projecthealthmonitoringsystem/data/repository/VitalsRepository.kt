package com.cse3310.projecthealthmonitoringsystem.data.repository

import com.cse3310.projecthealthmonitoringsystem.data.dao.VitalSignDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.VitalSignWithType
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalSign
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import kotlinx.coroutines.flow.Flow
import com.cse3310.projecthealthmonitoringsystem.settings.MeasurementUnits
import com.cse3310.projecthealthmonitoringsystem.validation.GlucoseContext
import com.cse3310.projecthealthmonitoringsystem.validation.VitalAssessment

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

    /**
     * Entry boundary: the submitted unit is frozen with the confirmed reading. Conversion occurs
     * exactly once, before validation/comparison in the VitalType's storage unit.
     * Retrying the same confirmed submission returns its existing record rather than a second alert.
     */
    suspend fun addEnteredVitalSign(userId: String, typeId: Long, value: Double, unit: String,
        secondaryValue: Double?, recordedAt: Long, context: GlucoseContext = GlucoseContext.SAVED_RANGE): AddVitalResult? = tx.run {
        val type = vitalSignDao.getVitalType(typeId) ?: return@run null
        val stored = if (MeasurementUnits.temperature(type.name)) {
            require(unit == "°F" || unit == "°C") { "Choose Fahrenheit or Celsius." }
            when {
                unit == type.unit -> value
                unit == "°F" && type.unit == "°C" -> MeasurementUnits.celsius(value)
                unit == "°C" && type.unit == "°F" -> MeasurementUnits.fahrenheit(value)
                else -> throw IllegalArgumentException("The saved temperature unit is not supported.")
            }
        } else {
            require(unit == type.unit) { "The measurement unit changed. Please check your reading." }
            value
        }
        validate(type, stored, secondaryValue)
        val note = if (type.name.contains("glucose", true)) context.note() else ""
        val existing = vitalSignDao.matchingSubmission(userId, typeId, stored, secondaryValue, recordedAt, note)
        if (existing != null) return@run AddVitalResult(existing.vitalId, existing.isAbnormal,
            if (existing.isAbnormal) monitoringRepository.raiseAbnormalVitalAlert(userId, existing.vitalId) else null)
        addVitalSign(userId, typeId, stored, secondaryValue, recordedAt, note)
    }

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
        val abnormal = VitalAssessment.isAbnormal(type, value, secondaryValue, GlucoseContext.fromNote(note))
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
            monitoringRepository.raiseAbnormalVitalAlert(userId, vitalId)
        } else null
        AddVitalResult(vitalId, abnormal, alertId)
    }

    /** Recomputes the flag and synchronizes the source-linked alert without duplicating it. */
    suspend fun editVitalSign(userId: String, vital: VitalSign): Boolean = tx.run {
        require(vital.recordedAt in 1..4102444799999L) { "Choose a valid reading date." }
        val type = vitalSignDao.getVitalType(vital.vitalTypeId) ?: return@run false
        validate(type, vital.value, vital.secondaryValue)
        val abnormal = VitalAssessment.isAbnormal(type, vital.value, vital.secondaryValue, GlucoseContext.fromNote(vital.note))
        val updated = vitalSignDao.updateVitalSign(userId, vital.copy(isAbnormal = abnormal))
        if (updated) {
            if (abnormal) monitoringRepository.raiseAbnormalVitalAlert(userId, vital.vitalId)
            monitoringRepository.repairVitalAlerts(userId)
        }
        updated
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

}
