package com.cse3310.projecthealthmonitoringsystem.data.repository

import com.cse3310.projecthealthmonitoringsystem.data.dao.VaccinationDao
import com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object VaccinationDates {
    fun parse(value: String): Long? {
        if (!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return null
        return runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)?.time }.getOrNull()
    }
    fun iso(time: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))
    fun label(value: String) = parse(value)?.let { SimpleDateFormat("MM/dd/yyyy", Locale.US).format(Date(it)) } ?: value
    fun reminderAt(value: String): Long? = parse(value)?.let { at ->
        Calendar.getInstance().apply { timeInMillis = at; set(Calendar.HOUR_OF_DAY, 9) }.timeInMillis
    }
    fun issue(record: Vaccination, now: Long, original: Vaccination? = null): String? = when {
        record.name.trim().isEmpty() -> "Enter the vaccine name."
        record.name.trim().length > 120 -> "Use 120 characters or fewer for the vaccine name."
        parse(record.receivedDate) == null -> "Choose a valid date received."
        record.receivedDate > iso(now) -> "Date received cannot be in the future."
        record.doseNumber != null && record.doseNumber <= 0 -> "Dose number must be a positive whole number."
        record.nextDoseDate != null && parse(record.nextDoseDate) == null -> "Choose a valid next dose date."
        record.nextDoseDate != null && record.nextDoseDate < record.receivedDate -> "Next dose date cannot precede the date received."
        record.reminderEnabled && !(original?.reminderEnabled == true && original.nextDoseDate == record.nextDoseDate) && (record.nextDoseDate == null || (reminderAt(record.nextDoseDate) ?: 0) <= now) ->
            "Choose a future next dose date. Reminders are scheduled for 9:00 AM."
        else -> null
    }
}
class VaccinationRepository(private val dao: VaccinationDao, private val clock: () -> Long = System::currentTimeMillis) {
    fun observe(owner: String) = dao.observe(owner)
    suspend fun get(owner: String, id: Long) = dao.get(owner, id)
    suspend fun save(owner: String, record: Vaccination): Long {
        require(owner.isNotBlank() && record.userId == owner) { "Please sign in again." }
        val original = if (record.vaccinationId > 0) dao.get(owner, record.vaccinationId) else null
        val issue = VaccinationDates.issue(record, clock(), original)
        require(issue == null) { issue.orEmpty() }
        return dao.save(owner, record.copy(name = record.name.trim(), providerOrPharmacy = record.providerOrPharmacy.trim(),
            location = record.location.trim(), notes = record.notes.trim()))
    }
    suspend fun delete(owner: String, id: Long) = dao.delete(owner, id) == 1
    suspend fun claimReminder(owner: String, id: Long, date: String) = dao.claimReminder(owner, id, date) == 1
}
