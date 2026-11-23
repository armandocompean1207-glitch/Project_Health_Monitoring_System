package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.communication.updateTitle
import com.cse3310.projecthealthmonitoringsystem.communication.updatePreview
import com.cse3310.projecthealthmonitoringsystem.data.entity.*
import com.cse3310.projecthealthmonitoringsystem.monitoring.AlertHandoff
import com.cse3310.projecthealthmonitoringsystem.settings.MeasurementPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommunicationRefinementTest : DbTest() {
    private suspend fun rejects(block: suspend () -> Unit) {
        try { block(); fail("Expected validation error") } catch (_: IllegalArgumentException) { }
    }

    @Test fun allSevenMethodCombinationsSaveReloadEditAndRemainUserScoped() = runBlocking<Unit> {
        for (mask in 1..7) {
            val contact = Designee(userId = A, name = "Fictional Contact $mask", phone = "+12025550142",
                email = "fictional$mask@example.com", preferredMethod = ContactMethod.EMAIL,
                contactMethodsMask = mask, automaticSmsConsent = false)
            val id = communication.addDesignee(A, contact)
            val saved = communication.getDesignee(A, id)!!
            assertEquals(mask, Designee.methodsMask(saved.selectedMethods()))
            assertFalse(AlertHandoff.usesAutomaticSms(saved))
            assertNull(communication.getDesignee(B, id))
            assertFalse(communication.editDesignee(B, saved))
            assertTrue(communication.editDesignee(A, saved.copy(contactMethodsMask = 7)))
            assertEquals(ContactMethod.entries.toSet(), communication.getDesignee(A, id)!!.selectedMethods())
        }
        assertEquals(7, communication.observeDesignees(A).first().size)
        assertTrue(communication.observeDesignees(B).first().isEmpty())
    }

    @Test fun textSelectionNeverGrantsAutomaticSmsConsentAndLegacyConsentIsPreserved() = runBlocking<Unit> {
        val old = Designee(userId = A, name = "Fictional", phone = "+12025550142",
            email = "fictional@example.com", preferredMethod = ContactMethod.EMAIL)
        assertEquals(ContactMethod.entries.toSet(), old.selectedMethods())
        assertFalse(old.allowsAutomaticSms())
        assertTrue(old.copy(preferredMethod = ContactMethod.SMS).allowsAutomaticSms())
        for (mask in listOf(1, 3, 5, 7)) {
            val contact = old.copy(preferredMethod = ContactMethod.SMS, contactMethodsMask = mask, automaticSmsConsent = false)
            assertFalse(contact.allowsAutomaticSms())
            val id = communication.addDesignee(A, contact.copy(automaticSmsConsent = true))
            assertTrue(communication.getDesignee(A, id)!!.allowsAutomaticSms())
        }
        assertFalse(old.copy(contactMethodsMask = 2, automaticSmsConsent = true).allowsAutomaticSms())
    }

    @Test fun everySelectedMethodHasRequiredValidDestinationAndEmptySelectionsAreRejected() = runBlocking<Unit> {
        val valid = Designee(userId = A, name = "Fictional", email = "fictional@example.com",
            preferredMethod = ContactMethod.EMAIL, contactMethodsMask = 2, automaticSmsConsent = false)
        assertTrue(communication.addDesignee(A, valid) > 0)
        rejects { communication.addDesignee(A, valid.copy(contactMethodsMask = 0)) }
        rejects { communication.addDesignee(A, valid.copy(contactMethodsMask = 8)) }
        rejects { communication.addDesignee(A, valid.copy(contactMethodsMask = 1)) }
        rejects { communication.addDesignee(A, valid.copy(contactMethodsMask = 4)) }
        rejects { communication.addDesignee(A, valid.copy(email = "")) }
        rejects { communication.addDesignee(A, valid.copy(automaticSmsConsent = true)) }
        rejects { communication.addDesignee(A, valid.copy(email = "malformed")) }
        assertTrue(communication.addDesignee(A, valid.copy(email = "", phone = "+12025550142", contactMethodsMask = 5)) > 0)
    }

    private suspend fun abnormalTemperature(at: Long = now): com.cse3310.projecthealthmonitoringsystem.data.repository.VitalsRepository.AddVitalResult {
        val type = vitals.observeVitalTypes().first().first { it.name == "Body Temperature" }
        return vitals.addVitalSign(A, type.vitalTypeId, 39.0, recordedAt = at)!!
    }

    @Test fun shareableViewExcludesAcknowledgedCorrectedSupersededAndOtherUsersWithoutDeletingHistory() = runBlocking<Unit> {
        val original = abnormalTemperature()
        assertEquals(original.alertId, monitoring.observeShareableAlerts(A).first().single().alertId)
        assertNull(monitoring.getShareableAlert(B, original.alertId!!))
        val old = vitals.getVitalSign(A, original.vitalId)!!
        now++
        vitals.addVitalSign(A, old.vitalTypeId, 36.6, recordedAt = now)
        assertNull(monitoring.getShareableAlert(A, original.alertId))
        assertTrue(monitoring.observeShareableAlerts(A).first().isEmpty())
        assertEquals(1, monitoring.observeAlerts(A).first().size)
        now++
        val latest = abnormalTemperature()
        assertNotNull(monitoring.getShareableAlert(A, latest.alertId!!))
        assertTrue(monitoring.acknowledgeAlert(A, latest.alertId))
        assertNull(monitoring.getShareableAlert(A, latest.alertId))
        assertEquals(2, monitoring.observeAlerts(A).first().size)
        now++
        val corrected = abnormalTemperature()
        val source = vitals.getVitalSign(A, corrected.vitalId)!!
        assertTrue(vitals.editVitalSign(A, source.copy(value = 36.6)))
        assertNull(monitoring.getShareableAlert(A, corrected.alertId!!))
    }

    @Test fun eligibilityIsRecheckedAfterPreviewAndAfterSourceDeletion() = runBlocking<Unit> {
        val result = abnormalTemperature()
        val selected = monitoring.getShareableAlert(A, result.alertId!!)!!
        assertTrue(monitoring.acknowledgeAlert(A, selected.alertId))
        // This is the same fresh repository gate used immediately before opening the external draft.
        assertNull(monitoring.getShareableAlert(A, selected.alertId))
        now++
        val other = abnormalTemperature()
        assertTrue(vitals.deleteVitalSign(A, other.vitalId))
        assertNull(monitoring.getShareableAlert(A, other.alertId!!))
        assertEquals(2, monitoring.observeAlerts(A).first().size)
    }

    @Test fun duplicateVitalAlertsAreSupersededAndExplicitExpirationCutoffDoesNotDeleteHistory() = runBlocking<Unit> {
        val result = abnormalTemperature(NOON - DAY)
        val first = monitoring.getAlert(A, result.alertId!!)!!
        val duplicate = db.alertDao().insertAlert(first.copy(alertId = 0, createdAt = now + 1))
        now += 2
        assertEquals(duplicate, monitoring.observeShareableAlerts(A).first().single().alertId)
        assertNull(monitoring.getShareableAlert(A, first.alertId))
        assertNull(monitoring.getShareableAlert(A, duplicate, createdAfter = now))
        assertEquals(2, monitoring.observeAlerts(A).first().size)
    }

    @Test fun missedDoseSharingRequiresCurrentMissedOccurrenceAndActiveUnexpiredTreatment() = runBlocking<Unit> {
        val med = addMedication(A, "Fictional medication")
        val missed = medications.scheduleDose(A, med, now - HOUR)!!
        val alert = monitoring.checkMissedDose(A, missed)!!
        assertNotNull(monitoring.getShareableAlert(A, alert.alertId))
        val next = medications.scheduleDose(A, med, now - MINUTE)!!
        assertNull(monitoring.getShareableAlert(A, alert.alertId))
        now += HOUR
        val latest = monitoring.checkMissedDose(A, next)!!
        assertNotNull(monitoring.getShareableAlert(A, latest.alertId))
        assertTrue(medications.editMedication(A, medications.getMedication(A, med)!!.copy(endDate = now - DAY)))
        assertNull(monitoring.getShareableAlert(A, latest.alertId))
        assertEquals(2, monitoring.observeAlerts(A).first().size)
        assertEquals(DoseStatus.MISSED, medications.getDose(A, missed)!!.status)
    }

    @Test fun removedConflictCannotBeSharedButItsAlertStaysInMonitoring() = runBlocking<Unit> {
        val warfarin = addMedication(A, "Warfarin")
        addMedication(A, "Aspirin")
        val pair = medications.detectConflicts(A).single()
        val id = monitoring.raiseConflictAlerts(A, listOf(pair)).single()
        assertNotNull(monitoring.getShareableAlert(A, id))
        assertTrue(medications.deactivateMedication(A, warfarin))
        assertNull(monitoring.getShareableAlert(A, id))
        assertNotNull(monitoring.getAlert(A, id))
    }

    @Test fun pickerLabelsContainNoReadingsDatesOrGuidanceAndPreviewHasPreferredUnits() = runBlocking<Unit> {
        val result = abnormalTemperature()
        val alert = monitoring.getShareableAlert(A, result.alertId!!)!!
        val source = vitals.observeVitalSigns(A).first().single()
        val types = vitals.observeVitalTypes().first()
        assertEquals("Abnormal Body Temperature", updateTitle(alert, source, types.first { it.vitalTypeId == source.vital.vitalTypeId }))
        val fahrenheit = updatePreview(alert, source, MeasurementPreferences())
        assertTrue(fahrenheit.contains("102.2 °F"))
        assertFalse(fahrenheit.contains("°C"))
        assertTrue(fahrenheit.contains("Recorded on"))
        assertTrue(updatePreview(alert, source, MeasurementPreferences(false)).contains("39.0 °C"))
        val bp = types.first { it.name == "Blood Pressure" }
        val bpResult = vitals.addVitalSign(A, bp.vitalTypeId, 150.0, 90.0)!!
        val bpAlert = monitoring.getShareableAlert(A, bpResult.alertId!!)!!
        val bpSource = vitals.observeVitalSigns(A).first().first { it.vital.vitalId == bpResult.vitalId }
        assertEquals("High Blood Pressure", updateTitle(bpAlert, bpSource, bp))
        val glucose = types.first { it.name == "Blood Glucose" }
        val low = vitals.addVitalSign(A, glucose.vitalTypeId, 60.0)!!
        assertEquals("Low Blood Glucose", updateTitle(monitoring.getShareableAlert(A, low.alertId!!)!!,
            vitals.observeVitalSigns(A).first().first { it.vital.vitalId == low.vitalId }, glucose))
    }
}
