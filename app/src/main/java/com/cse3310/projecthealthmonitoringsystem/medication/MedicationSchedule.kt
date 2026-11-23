package com.cse3310.projecthealthmonitoringsystem.medication

import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes
import java.util.Calendar
import java.util.TimeZone

/** Display-only schedule. Never inserts future DoseLog rows or changes existing dose history. */
fun scheduledIntakes(med: Medication, from: Long, to: Long, zone: TimeZone = TimeZone.getDefault()): List<Long> {
    if (!med.isActive || from > to || to < med.startDate || (med.endDate != null && from > med.endDate)) return emptyList()
    if (med.doseTimesMinutes == null) {
        // Keep prescriptions created before explicit dose times exactly as they were until edited.
        val frequency = Frequency.timesPerDay(med.frequency)
        var at = DoseTimes.nextDoseTime(med.intakeTimeMinutes / 60, med.intakeTimeMinutes % 60, frequency, from - 1)
        val result = mutableListOf<Long>()
        while (at <= to) {
            if (at >= med.startDate && (med.endDate == null || at <= med.endDate)) result.add(at)
            val next = DoseTimes.nextAfter(at, DoseTimes.DAY_MILLIS / frequency, at)
            if (next <= at) break
            at = next
        }
        return result
    }
    val times = MedicationTimes.decode(med.doseTimesMinutes) ?: return emptyList()
    val lower = maxOf(from, med.startDate)
    val upper = minOf(to, med.endDate ?: Long.MAX_VALUE)
    val day = Calendar.getInstance(zone).apply { timeInMillis = MedicationTimes.dayStart(lower, zone) }
    val result = mutableSetOf<Long>()
    while (day.timeInMillis <= upper) {
        times.forEach { minutes ->
            MedicationTimes.onDay(day.timeInMillis, minutes, zone)?.takeIf { it in lower..upper }?.let(result::add)
        }
        val previous = day.timeInMillis
        day.add(Calendar.DAY_OF_MONTH, 1)
        if (day.timeInMillis <= previous) break
    }
    return result.sorted()
}

/** One reminder chain per medication, advancing to the next selected local time within treatment. */
fun nextScheduledIntake(med: Medication, after: Long, zone: TimeZone = TimeZone.getDefault()): Long? {
    if (!med.isActive || after == Long.MAX_VALUE || med.endDate?.let { it <= after } == true) return null
    if (med.doseTimesMinutes == null) {
        val next = DoseTimes.nextDoseTime(med.intakeTimeMinutes / 60, med.intakeTimeMinutes % 60,
            Frequency.timesPerDay(med.frequency), maxOf(after, med.startDate - 1))
        return next.takeIf { it >= med.startDate && (med.endDate == null || it <= med.endDate) }
    }
    val lower = maxOf(after + 1, med.startDate)
    val endOfLookahead = Calendar.getInstance(zone).apply {
        timeInMillis = MedicationTimes.dayStart(lower, zone); add(Calendar.DAY_OF_MONTH, 2)
    }.timeInMillis - 1
    return scheduledIntakes(med, lower, minOf(endOfLookahead, med.endDate ?: Long.MAX_VALUE), zone).firstOrNull()
}

fun isScheduledIntake(med: Medication, at: Long): Boolean = scheduledIntakes(med, at, at).isNotEmpty()
