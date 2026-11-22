package com.cse3310.projecthealthmonitoringsystem.medication

import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import java.util.Calendar
import java.util.TimeZone

/** Minutes after local midnight. The nullable database field distinguishes legacy schedules. */
object MedicationTimes {
    fun decode(value: String): List<Int>? {
        if (value.isBlank()) return null
        val parts = value.split(",")
        val times = parts.map { it.toIntOrNull() ?: return null }
        if (times.any { it !in 0..1439 } || times.distinct().size != times.size) return null
        return times.sorted()
    }
    fun encode(times: List<Int>): String {
        require(times.isNotEmpty() && times.all { it in 0..1439 } && times.distinct().size == times.size) {
            "Choose a different valid time for each dose."
        }
        return times.sorted().joinToString(",")
    }
    /** Legacy times are displayed for review, not rewritten until the user saves an edit. */
    fun forEditing(medication: Medication): List<Int> =
        medication.doseTimesMinutes?.let { decode(it).orEmpty() } ?: List(Frequency.timesPerDay(medication.frequency)) {
            (medication.intakeTimeMinutes + it * 1440 / Frequency.timesPerDay(medication.frequency)) % 1440
        }

    fun error(times: List<Int?>, frequency: Int, start: Long, end: Long?, ongoing: Boolean): String? = when {
        !ongoing && end == null -> "Choose an end date or select Ongoing medication."
        !ongoing && end != null && end < start -> "End date cannot be before start date."
        times.size != frequency || times.any { it == null || it !in 0..1439 } -> "Choose a time for every dose."
        times.distinct().size != times.size -> "Choose a different time for each dose."
        else -> null
    }

    fun dayStart(at: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = at; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun dayEnd(at: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(timeZone).apply { timeInMillis = dayStart(at, timeZone); add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis - 1

    internal fun onDay(day: Long, minutes: Int, zone: TimeZone): Long? {
        val candidate = Calendar.getInstance(zone).apply {
            timeInMillis = day; set(Calendar.HOUR_OF_DAY, minutes / 60); set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        // Do not silently move a prescribed time across a DST gap. One occurrence per local day;
        // Calendar resolves an ambiguous fall-back time to a single instant.
        if (candidate.get(Calendar.HOUR_OF_DAY) != minutes / 60 || candidate.get(Calendar.MINUTE) != minutes % 60) return null
        return candidate.timeInMillis
    }
}
