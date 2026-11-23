package com.cse3310.projecthealthmonitoringsystem.data.repository

import com.cse3310.projecthealthmonitoringsystem.data.dao.DoctorVisitWithDoctor
import com.cse3310.projecthealthmonitoringsystem.data.dao.SearchDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.SearchResult
import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import java.util.Calendar
import java.util.TimeZone

/** Search (Diyanshu): keyword search by data type, plus the four shortcuts. Read-only. */
class SearchRepository(
    private val searchDao: SearchDao,
    private val clock: Clock = SystemClock,
    private val timeZone: () -> TimeZone = { TimeZone.getDefault() }
) {

    enum class SearchCategory { MEDICATIONS, DOCTORS_AND_VISITS, VITAL_SIGNS, DIET_AND_WEIGHT, NOTES }

    /**
     * Searches one data type. A blank keyword matches everything in the category. An empty list means
     * "no results"; the screen shows that error.
     */
    suspend fun search(
        userId: String,
        category: SearchCategory,
        keyword: String,
        from: Long? = null,
        to: Long? = null
    ): List<SearchResult> {
        require(from == null || to == null || from <= to) { "Start date must not be after end date" }
        val pattern = likePattern(keyword)
        return when (category) {
            SearchCategory.MEDICATIONS -> (searchDao.searchMedications(userId, pattern, from, to) +
                searchDao.searchIntakeHistory(userId, pattern, from, to)).sortedByDescending { it.timestamp }
            SearchCategory.DOCTORS_AND_VISITS -> searchDao.searchDoctorsAndVisits(userId, pattern, from, to)
            SearchCategory.VITAL_SIGNS -> searchDao.searchVitalSigns(userId, pattern, from, to)
            SearchCategory.DIET_AND_WEIGHT -> searchDao.searchDietAndWeight(userId, pattern, from, to)
            SearchCategory.NOTES -> searchDao.searchNotes(userId, pattern, from, to)
        }
    }

    suspend fun nextDoctorVisit(userId: String): DoctorVisitWithDoctor? = searchDao.getNextDoctorVisit(userId, clock())

    suspend fun lastDoctorVisit(userId: String): DoctorVisitWithDoctor? = searchDao.getLastDoctorVisit(userId, clock())

    suspend fun pastMedications(userId: String): List<Medication> = searchDao.getPastMedications(userId, clock())

    /** Next annual checkup. A stored date in the past is rolled forward a year at a time. */
    suspend fun nextAnnualCheckup(userId: String): Long? {
        val stored = searchDao.getAnnualCheckupAt(userId) ?: return null
        val now = clock()
        if (stored >= now) return stored
        val cal = Calendar.getInstance(timeZone()).apply { timeInMillis = stored }
        while (cal.timeInMillis < now) cal.add(Calendar.YEAR, 1)
        return cal.timeInMillis
    }

    companion object {
        /** Wraps the keyword in % and escapes LIKE wildcards so "50%" matches literally. */
        fun likePattern(keyword: String): String {
            val escaped = keyword.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            return "%$escaped%"
        }
    }
}
