package com.cse3310.projecthealthmonitoringsystem.monitoring

import android.content.Context
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert
import com.cse3310.projecthealthmonitoringsystem.data.entity.AlertType
import com.cse3310.projecthealthmonitoringsystem.data.entity.ContactMethod
import com.cse3310.projecthealthmonitoringsystem.data.entity.DeliveryStatus

// Monitoring -> Communication: tell the user, then text each designee and log the result.
object AlertHandoff {

    fun title(type: AlertType): String = when (type) {
        AlertType.MISSED_DOSE -> "Missed dose"
        AlertType.CONFLICT -> "Medication conflict"
        AlertType.ABNORMAL_VITAL -> "Abnormal vital sign"
    }

    fun usesAutomaticSms(contact: com.cse3310.projecthealthmonitoringsystem.data.entity.Designee): Boolean =
        contact.preferredMethod == ContactMethod.SMS

    suspend fun send(context: Context, alert: Alert): String {
        val app = context.phms()
        val userId = alert.userId
        if (!com.cse3310.projecthealthmonitoringsystem.auth.AccountSession.whileActive(context, userId) {
            AlertSender.notifyUser(context, (100_000 + alert.alertId).toInt(), title(alert.type), alert.message)
        }) return "Account is signed out"

        val designees = app.communicationRepository.getDesignees(userId).filter(::usesAutomaticSms)
        if (designees.isEmpty()) return "No automatic SMS contacts. Open Communication for email or calls."

        val text = "PHMS alert: ${alert.message}"
        val texted = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (d in designees) {
            var ok = false
            if (!com.cse3310.projecthealthmonitoringsystem.auth.AccountSession.whileActive(context, userId) {
                ok = AlertSender.sendSms(context, d.phone, text)
            }) return "Account is signed out"
            app.communicationRepository.logCommunication(
                userId = userId,
                alertId = alert.alertId,
                designeeId = d.designeeId,
                method = ContactMethod.SMS,
                status = if (ok) DeliveryStatus.SENT else DeliveryStatus.FAILED,
                detail = if (ok) "Submitted to SMS service; delivery is not confirmed" else "No phone number, SMS permission, or SMS service"
            )
            if (ok) texted += d.name else failed += d.name
        }
        return buildString {
            if (texted.isNotEmpty()) append("SMS submitted for ${texted.joinToString()}")
            if (failed.isNotEmpty()) {
                if (isNotEmpty()) append("; ")
                append("could not text ${failed.joinToString()}")
            }
        }
    }
}
