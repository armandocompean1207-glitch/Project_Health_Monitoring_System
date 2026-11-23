package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.*
import com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccinationDao {
    @Query("SELECT * FROM Vaccination WHERE userId=:userId ORDER BY receivedDate DESC, vaccinationId DESC")
    fun observe(userId: String): Flow<List<Vaccination>>

    @Query("SELECT * FROM Vaccination WHERE userId=:userId AND vaccinationId=:id")
    suspend fun get(userId: String, id: Long): Vaccination?

    @Query("SELECT COUNT(*) FROM Vaccination WHERE userId=:owner AND name=:name COLLATE NOCASE AND receivedDate=:date AND doseNumber IS :dose AND vaccinationId!=:exceptId")
    suspend fun duplicates(owner: String, name: String, date: String, dose: Int?, exceptId: Long): Int

    @Insert
    suspend fun insert(record: Vaccination): Long

    @Query("""UPDATE Vaccination SET name=:name, receivedDate=:received, doseNumber=:dose,
        providerOrPharmacy=:provider, location=:location, nextDoseDate=:next, notes=:notes,
        reminderEnabled=:enabled, reminderNotifiedFor=:notified
        WHERE userId=:owner AND vaccinationId=:id""")
    suspend fun update(owner: String, id: Long, name: String, received: String, dose: Int?, provider: String,
        location: String, next: String?, notes: String, enabled: Boolean, notified: String?): Int

    @Transaction
    suspend fun save(owner: String, record: Vaccination): Long {
        require(record.userId == owner) { "This record belongs to another account." }
        require(duplicates(owner, record.name, record.receivedDate, record.doseNumber, record.vaccinationId) == 0) {
            "This vaccination is already recorded."
        }
        if (record.vaccinationId == 0L) return insert(record.copy(reminderNotifiedFor = null))
        val original = checkNotNull(get(owner, record.vaccinationId)) { "This vaccination is no longer available." }
        val notified = original.reminderNotifiedFor.takeIf { original.nextDoseDate == record.nextDoseDate }
        check(update(owner, record.vaccinationId, record.name, record.receivedDate, record.doseNumber,
            record.providerOrPharmacy, record.location, record.nextDoseDate, record.notes,
            record.reminderEnabled, notified) == 1)
        return record.vaccinationId
    }

    @Query("DELETE FROM Vaccination WHERE userId=:owner AND vaccinationId=:id")
    suspend fun delete(owner: String, id: Long): Int

    @Query("""UPDATE Vaccination SET reminderNotifiedFor=:date WHERE userId=:owner AND vaccinationId=:id
        AND reminderEnabled=1 AND nextDoseDate=:date AND (reminderNotifiedFor IS NULL OR reminderNotifiedFor!=:date)""")
    suspend fun claimReminder(owner: String, id: Long, date: String): Int
}
