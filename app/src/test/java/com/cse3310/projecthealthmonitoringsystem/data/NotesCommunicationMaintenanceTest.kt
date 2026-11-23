package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.ContactMethod
import com.cse3310.projecthealthmonitoringsystem.data.entity.DeliveryStatus
import com.cse3310.projecthealthmonitoringsystem.data.entity.Designee
import com.cse3310.projecthealthmonitoringsystem.data.entity.Doctor
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.data.entity.Specialty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesCommunicationMaintenanceTest : DbTest() {

    @Test
    fun noteLifecycle() = runBlocking<Unit> {
        val id = notes.createNote(A, " Diet plan ", "Less sugar")
        val created = notes.openNote(A, id)!!
        assertEquals("Diet plan", created.title)
        assertEquals(now, created.createdAt)
        assertEquals(now, created.editedAt)

        now += HOUR
        assertTrue(notes.editNote(A, id, "Diet plan", "Less sugar, more protein"))
        val edited = notes.openNote(A, id)!!
        assertEquals(NOON, edited.createdAt)
        assertEquals(now, edited.editedAt)

        notes.createNote(A, "Second", "")
        assertEquals(listOf("Second", "Diet plan"), notes.observeNotes(A).first().map { it.title })
        assertTrue(notes.deleteNote(A, id))
        assertNull(notes.openNote(A, id))
    }

    @Test
    fun designeeAndDeliveryLog() = runBlocking<Unit> {
        val id = communication.addDesignee(A, Designee(userId = A, name = "Mom", phone = "8175550101", preferredMethod = ContactMethod.SMS))
        assertEquals(listOf("Mom"), communication.getDesignees(A).map { it.name })
        assertTrue(communication.editDesignee(A, communication.getDesignee(A, id)!!.copy(preferredMethod = ContactMethod.EMAIL, email = "fictional@example.com")))
        assertEquals(ContactMethod.EMAIL, communication.observeDesignees(A).first().single().preferredMethod)

        val med = addMedication(A, "Aspirin")
        val dose = medications.scheduleDose(A, med, NOON)!!
        val alert = monitoring.checkMissedDose(A, dose, now = NOON + HOUR)!!
        communication.logCommunication(A, alert.alertId, id, ContactMethod.SMS, DeliveryStatus.FAILED, "No service", attempt = 1)
        communication.logCommunication(A, alert.alertId, id, ContactMethod.SMS, DeliveryStatus.FAILED, "No service", attempt = 2)
        communication.logCommunication(A, alert.alertId, id, ContactMethod.SMS, DeliveryStatus.SENT, attempt = 3)
        assertEquals(2, communication.failedSmsAttempts(A, alert.alertId, id))
        assertEquals(3, communication.getLogsForAlert(A, alert.alertId).size)
        assertEquals(3, communication.observeCommunicationLog(A).first().size)

        // deleting the designee keeps the log, with no designee
        assertTrue(communication.deleteDesignee(A, id))
        assertTrue(communication.getLogsForAlert(A, alert.alertId).all { it.designeeId == null })
    }

    @Test
    fun doctorFormAndVisitOwnership() = runBlocking<Unit> {
        val doc = maintenance.addDoctor(A, Doctor(userId = A, name = "Dr. A", email = "a@clinic.com"))
        assertTrue(maintenance.editDoctor(A, maintenance.getDoctor(A, doc)!!.copy(phone = "8175550102")))
        assertEquals("8175550102", maintenance.getDoctor(A, doc)!!.phone)

        val bDoc = addDoctor(B)
        assertNull(maintenance.addDoctorVisit(A, DoctorVisit(userId = A, doctorId = bDoc, visitAt = NOON)))
        val visit = maintenance.addDoctorVisit(A, DoctorVisit(userId = A, doctorId = doc, visitAt = NOON))!!
        assertFalse(maintenance.editDoctorVisit(A, maintenance.getDoctorVisit(A, visit)!!.copy(doctorId = bDoc)))
        assertEquals("Dr. A", maintenance.observeDoctorVisits(A).first().single().doctorName)

        // a medication keeps its data when its doctor is deleted
        val med = requireNotNull(medications.addMedication(A, medication(A, "Aspirin", doctorId = doc)))
        assertTrue(maintenance.deleteDoctor(A, doc))
        assertNull(medications.getMedication(A, med)!!.doctorId)
        assertTrue(maintenance.observeDoctorVisits(A).first().isEmpty())
    }

    @Test
    fun specialtyLookupIsEditable() = runBlocking<Unit> {
        val id = maintenance.addSpecialty(" Allergy ")
        assertNotNull(id)
        assertNull(maintenance.addSpecialty("Allergy"))
        assertTrue(maintenance.editSpecialty(Specialty(id!!, "Allergy and Immunology")))
        assertFalse(maintenance.editSpecialty(Specialty(id, "Cardiology"))) // duplicate name
        val doc = maintenance.addDoctor(A, Doctor(userId = A, name = "Dr. S", specialtyId = id))
        assertTrue(maintenance.deleteSpecialty(id))
        assertNull(maintenance.getDoctor(A, doc)!!.specialtyId)
        assertTrue(maintenance.observeSpecialties().first().none { it.specialtyId == id })
    }
}
