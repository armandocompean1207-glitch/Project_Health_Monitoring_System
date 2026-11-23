package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Intent
import com.cse3310.projecthealthmonitoringsystem.data.entity.*
import com.cse3310.projecthealthmonitoringsystem.monitoring.AlertHandoff
import com.cse3310.projecthealthmonitoringsystem.monitoring.AlertSender
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommunicationSafetyTest : DbTest() {
    @Test fun manualIntentsAreDraftsAndDialerNeverDirectCalls() {
        val phone = "+12025550123"
        val sms = AlertSender.smsIntent(phone, "Fictional PHMS alert")
        assertEquals(Intent.ACTION_SENDTO, sms.action)
        assertEquals("smsto", sms.data!!.scheme)
        assertEquals("Fictional PHMS alert", sms.getStringExtra("sms_body"))
        val email = AlertSender.emailIntent("fictional@example.com", "PHMS test", "Fictional alert")
        assertEquals(Intent.ACTION_SENDTO, email.action)
        assertEquals("mailto", email.data!!.scheme)
        assertEquals(Intent.ACTION_DIAL, AlertSender.dialIntent(phone).action)
    }

    @Test fun preferredMethodControlsAutomaticSms() {
        val contact = Designee(userId = A, name = "Fictional Contact", phone = "+12025550123",
            email = "fictional@example.com", preferredMethod = ContactMethod.EMAIL)
        assertFalse(AlertHandoff.usesAutomaticSms(contact))
        assertFalse(AlertHandoff.usesAutomaticSms(contact.copy(preferredMethod = ContactMethod.CALL)))
        assertTrue(AlertHandoff.usesAutomaticSms(contact.copy(preferredMethod = ContactMethod.SMS)))
    }

    @Test fun contactEditingAndAlertLogsRemainPrivateAndSurviveContactRemoval() = runBlocking<Unit> {
        val contact = Designee(userId = A, name = "Fictional Contact", email = "fictional@example.com", preferredMethod = ContactMethod.EMAIL)
        val id = communication.addDesignee(A, contact)
        assertTrue(communication.editDesignee(A, contact.copy(designeeId = id, relationship = "Demo caregiver")))
        assertFalse(communication.editDesignee(B, contact.copy(designeeId = id)))
        assertNull(communication.getDesignee(B, id))
        val medication = addMedication(A, "Fictional medicine")
        val dose = medications.scheduleDose(A, medication, NOON)!!
        val alert = monitoring.checkMissedDose(A, dose, now = NOON + HOUR)!!
        assertNotNull(communication.logCommunication(A, alert.alertId, id, ContactMethod.EMAIL, DeliveryStatus.OPENED))
        assertNull(communication.logCommunication(B, alert.alertId, id, ContactMethod.EMAIL, DeliveryStatus.OPENED))
        assertTrue(communication.observeCommunicationLog(B).first().isEmpty())
        assertTrue(communication.deleteDesignee(A, id))
        assertNull(communication.getLogsForAlert(A, alert.alertId).single().designeeId)
        assertTrue(monitoring.acknowledgeAlert(A, alert.alertId))
        assertFalse(monitoring.acknowledgeAlert(B, alert.alertId))
        assertNotNull(monitoring.observeAlerts(A).first().single().acknowledgedAt)
        assertEquals(DoseStatus.MISSED, medications.getDose(A, dose)!!.status)
    }
}
