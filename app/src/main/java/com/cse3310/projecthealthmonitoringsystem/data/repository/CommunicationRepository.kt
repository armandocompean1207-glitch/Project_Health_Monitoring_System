package com.cse3310.projecthealthmonitoringsystem.data.repository

import com.cse3310.projecthealthmonitoringsystem.data.dao.CommunicationLogDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.DesigneeDao
import com.cse3310.projecthealthmonitoringsystem.data.entity.CommunicationLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.ContactMethod
import com.cse3310.projecthealthmonitoringsystem.data.entity.DeliveryStatus
import com.cse3310.projecthealthmonitoringsystem.data.entity.Designee
import kotlinx.coroutines.flow.Flow

/**
 * Communication (Nathan): designees and the delivery log. Sending (SendSMS with SmsManager, email and
 * dial intents) happens in communication/; this class only stores who to contact and what happened.
 */
class CommunicationRepository(
    private val designeeDao: DesigneeDao,
    private val communicationLogDao: CommunicationLogDao,
    private val clock: Clock = SystemClock
) {

    companion object {
        /** Should: SMS retries up to 3, then tell the user. */
        const val MAX_SMS_ATTEMPTS = 3
    }

    // ---- Designees ----

    suspend fun addDesignee(userId: String, designee: Designee): Long {
        require(designee.userId == userId) { "Designee must belong to the logged-in user" }
        validateContact(designee)
        return designeeDao.insertDesignee(designee)
    }

    suspend fun editDesignee(userId: String, designee: Designee): Boolean {
        validateContact(designee)
        return designeeDao.updateDesignee(userId, designee)
    }
    private fun validateContact(contact: Designee) {
        val v = com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
        (v.name(contact.name, "Contact name") ?: v.phone(contact.phone) ?: v.email(contact.email))
            ?.let { throw IllegalArgumentException(it) }
        require(contact.contactMethodsMask == null || contact.contactMethodsMask in 1..7) { "Select at least one contact method." }
        val methods = contact.selectedMethods()
        require(methods.isNotEmpty()) { "Select at least one contact method." }
        require(ContactMethod.EMAIL !in methods || contact.email.isNotBlank()) { "Enter an email address for this contact." }
        require(methods.none { it == ContactMethod.SMS || it == ContactMethod.CALL } || contact.phone.isNotBlank()) {
            "Enter a phone number for texts and calls."
        }
        require(contact.automaticSmsConsent != true || ContactMethod.SMS in methods) {
            "Select Text before allowing automatic SMS alerts."
        }
    }

    suspend fun deleteDesignee(userId: String, designeeId: Long): Boolean = designeeDao.deleteDesignee(userId, designeeId) > 0

    suspend fun getDesignee(userId: String, designeeId: Long): Designee? = designeeDao.getDesignee(userId, designeeId)

    fun observeDesignees(userId: String): Flow<List<Designee>> = designeeDao.observeDesignees(userId)

    suspend fun getDesignees(userId: String): List<Designee> = designeeDao.getDesignees(userId)

    // ---- Delivery log ----

    /**
     * Records one delivery attempt. Returns the new commId, or null if [alertId] or [designeeId]
     * belongs to another user.
     */
    suspend fun logCommunication(
        userId: String,
        alertId: Long?,
        designeeId: Long?,
        method: ContactMethod,
        status: DeliveryStatus,
        detail: String = "",
        attempt: Int = 1
    ): Long? {
        if (alertId != null && !communicationLogDao.alertOwned(userId, alertId)) return null
        if (designeeId != null && !designeeDao.designeeOwned(userId, designeeId)) return null
        return insertLog(userId, alertId, designeeId, method, status, detail, attempt)
    }

    private suspend fun insertLog(
        userId: String,
        alertId: Long?,
        designeeId: Long?,
        method: ContactMethod,
        status: DeliveryStatus,
        detail: String,
        attempt: Int
    ): Long = communicationLogDao.insertLog(
        CommunicationLog(
            userId = userId,
            alertId = alertId,
            designeeId = designeeId,
            method = method,
            status = status,
            attempt = attempt,
            detail = detail,
            sentAt = clock()
        )
    )

    fun observeCommunicationLog(userId: String): Flow<List<CommunicationLog>> = communicationLogDao.observeLogs(userId)

    suspend fun getLogsForAlert(userId: String, alertId: Long): List<CommunicationLog> =
        communicationLogDao.getLogsForAlert(userId, alertId)

    suspend fun failedSmsAttempts(userId: String, alertId: Long, designeeId: Long): Int =
        communicationLogDao.countFailedAttempts(userId, alertId, designeeId)

    suspend fun deleteLog(userId: String, commId: Long): Boolean = communicationLogDao.deleteLog(userId, commId) > 0
}
