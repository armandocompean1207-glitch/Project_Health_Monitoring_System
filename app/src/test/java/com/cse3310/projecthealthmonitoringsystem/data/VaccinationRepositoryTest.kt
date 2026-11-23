package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination
import com.cse3310.projecthealthmonitoringsystem.data.repository.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VaccinationRepositoryTest : DbTest() {
    private fun repo() = VaccinationRepository(db.vaccinationDao(), clock)
    private fun record() = Vaccination(userId = A, name = "Fictional vaccine",
        receivedDate = VaccinationDates.iso(now), notes = "User-entered record")
    @Test fun crudIsScopedAndDuplicatesAreRejected() = runBlocking<Unit> {
        val repo = repo()
        val id = repo.save(A, record())
        val saved = repo.get(A, id)!!
        assertNull(repo.get(B, id))
        assertTrue(repo.observe(B).first().isEmpty())
        assertTrue(runCatching { repo.save(A, record()) }.isFailure)
        assertTrue(runCatching { repo.save(B, saved.copy(userId = B, notes = "Not allowed")) }.isFailure)
        assertEquals("User-entered record", repo.get(A, id)!!.notes)
        repo.save(A, saved.copy(location = "Fictional clinic", doseNumber = 2))
        assertEquals(2, repo.get(A, id)!!.doseNumber)
        assertFalse(repo.delete(B, id))
        assertTrue(repo.delete(A, id))
        assertTrue(repo.observe(A).first().isEmpty())
    }
    @Test fun reminderClaimsAreScopedAndIdempotentAndChangingDateResetsClaim() = runBlocking<Unit> {
        val repo = repo()
        val date = VaccinationDates.iso(now + DAY)
        val id = repo.save(A, record().copy(nextDoseDate = date, reminderEnabled = true))
        assertFalse(repo.claimReminder(B, id, date))
        assertTrue(repo.claimReminder(A, id, date))
        assertFalse(repo.claimReminder(A, id, date))
        val saved = repo.get(A, id)!!
        repo.save(A, saved.copy(notes = "Updated notes"))
        assertEquals(date, repo.get(A, id)!!.reminderNotifiedFor)
        val next = VaccinationDates.iso(now + 2 * DAY)
        repo.save(A, saved.copy(nextDoseDate = next))
        assertNull(repo.get(A, id)!!.reminderNotifiedFor)
        assertFalse(repo.claimReminder(A, id, date))
        assertTrue(repo.claimReminder(A, id, next))
    }
    @Test fun datesAndOptionalDoseValidationDoNotInventSchedules() = runBlocking<Unit> {
        val repo = repo()
        val values = listOf(record().copy(name = ""), record().copy(receivedDate = "2026-02-30"),
            record().copy(receivedDate = VaccinationDates.iso(now + DAY)),
            record().copy(doseNumber = 0), record().copy(reminderEnabled = true),
            record().copy(nextDoseDate = VaccinationDates.iso(now - DAY)))
        values.forEach { assertTrue(runCatching { repo.save(A, it) }.isFailure) }
        val id = repo.save(A, record())
        assertNull(repo.get(A, id)!!.doseNumber)
        assertNull(repo.get(A, id)!!.nextDoseDate)
        assertFalse(repo.get(A, id)!!.reminderEnabled)
    }
}
