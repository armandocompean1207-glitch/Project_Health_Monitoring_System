package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.data.repository.MaintenanceRepository
import com.cse3310.projecthealthmonitoringsystem.maintenance.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AppointmentReminderTest : DbTest() {
    @Test fun sixOffsetsDefaultSelectionsAndPastTimesAreAccurate() {
        val visit = DoctorVisit(userId = A, doctorId = 1, visitAt = NOON + 10 * DAY, reminderMask = 63)
        val options = appointmentReminderTimes(visit, NOON)
        assertEquals(listOf(10080L, 1440L, 300L, 60L, 30L, 15L), options.map { it.first.minutes })
        options.forEach { (option, at) -> assertEquals(visit.visitAt - option.minutes * MINUTE, at) }
        assertEquals(listOf(AppointmentReminderOption.DAY, AppointmentReminderOption.HOUR),
            appointmentReminderTimes(visit.copy(reminderMask = 10), NOON).map { it.first })
        assertTrue(appointmentReminderTimes(visit.copy(reminderMask = 0), NOON).isEmpty())
        assertTrue(appointmentReminderTimes(visit.copy(cancelled = true), NOON).isEmpty())
        val close = appointmentReminderTimes(visit.copy(visitAt = NOON + HOUR), NOON)
        assertEquals(listOf(AppointmentReminderOption.HALF_HOUR, AppointmentReminderOption.QUARTER_HOUR), close.map { it.first })
        assertTrue(close.all { it.second > NOON })
    }
    @Test fun claimsAreScopedIdempotentAndOldRevisionsCannotNotifyAfterEditing() = runBlocking<Unit> {
        val doctor = addDoctor(A)
        val id = maintenance.addDoctorVisit(A, DoctorVisit(userId = A, doctorId = doctor, visitAt = now + 10 * DAY))!!
        val original = maintenance.getDoctorVisit(A, id)!!
        assertEquals(10, original.reminderMask)
        assertFalse(maintenance.claimAppointmentReminder(B, id, original.reminderVersion, original.visitAt, 2))
        assertTrue(maintenance.claimAppointmentReminder(A, id, original.reminderVersion, original.visitAt, 2))
        assertFalse(maintenance.claimAppointmentReminder(A, id, original.reminderVersion, original.visitAt, 2))
        assertTrue(maintenance.editDoctorVisit(A, original.copy(notes = "Same appointment")))
        val notesEdit = maintenance.getDoctorVisit(A, id)!!
        assertEquals(2, notesEdit.reminderSentMask)
        assertFalse(maintenance.claimAppointmentReminder(A, id, original.reminderVersion, original.visitAt, 8))
        assertTrue(maintenance.editDoctorVisit(A, notesEdit.copy(visitAt = original.visitAt + DAY)))
        val moved = maintenance.getDoctorVisit(A, id)!!
        assertEquals(0, moved.reminderSentMask)
        assertTrue(maintenance.cancelDoctorVisit(A, id))
        val cancelled = maintenance.getDoctorVisit(A, id)!!
        assertTrue(cancelled.cancelled)
        assertTrue(appointmentReminderTimes(cancelled, now).isEmpty())
        assertFalse(maintenance.claimAppointmentReminder(A, id, cancelled.reminderVersion, cancelled.visitAt, 8))
    }
    @Test fun saveDeleteAndProviderCascadeTriggerReschedulingWithoutTouchingOtherAccounts() = runBlocking<Unit> {
        val events = mutableListOf<Pair<String, Long>>()
        val repo = MaintenanceRepository(db.doctorDao(), db.lookupDao()) { owner, id -> events += owner to id }
        val aDoctor = addDoctor(A)
        val bDoctor = addDoctor(B)
        val a = repo.addDoctorVisit(A, DoctorVisit(userId = A, doctorId = aDoctor, visitAt = now + DAY))!!
        val b = repo.addDoctorVisit(B, DoctorVisit(userId = B, doctorId = bDoctor, visitAt = now + DAY))!!
        events.clear()
        assertFalse(repo.deleteDoctorVisit(B, a))
        assertTrue(events.isEmpty())
        assertTrue(repo.deleteDoctor(A, aDoctor))
        assertEquals(listOf(A to a), events)
        assertNull(repo.getDoctorVisit(A, a))
        assertNotNull(repo.getDoctorVisit(B, b))
        assertFalse(repo.claimAppointmentReminder(A, a, 0, now + DAY, 8))
    }
}
