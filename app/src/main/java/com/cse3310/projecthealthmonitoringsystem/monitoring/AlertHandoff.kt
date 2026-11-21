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

    suspend fun send(context: Context, alert: Alert): String {
        val app = context.phms()
        val userId = alert.userId
        AlertSender.notifyUser(context, (100_000 + alert.alertId).toInt(), title(alert.type), alert.message)

        val designees = app.communicationRepository.getDesignees(userId)
        if (designees.isEmpty()) return "No designees set up"

        val text = "PHMS alert: ${alert.message}"
        val texted = mutableListOf<String>()
        val failed = mutableListOf<String>()
        for (d in designees) {
            val ok = AlertSender.sendSms(context, d.phone, text)
            app.communicationRepository.logCommunication(
                userId = userId,
                alertId = alert.alertId,
                designeeId = d.designeeId,
                method = ContactMethod.SMS,
                status = if (ok) DeliveryStatus.SENT else DeliveryStatus.FAILED,
                detail = if (ok) "" else "No phone number or SMS permission"
            )
            if (ok) texted += d.name else failed += d.name
        }
        return buildString {
            if (texted.isNotEmpty()) append("Texted ${texted.joinToString()}")
            if (failed.isNotEmpty()) {
                if (isNotEmpty()) append("; ")
                append("could not text ${failed.joinToString()}")
            }
        }
    }
}
