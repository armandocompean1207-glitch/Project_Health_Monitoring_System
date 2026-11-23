package com.cse3310.projecthealthmonitoringsystem.medication

import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.data.repository.MonitoringRepository

/** Display-only projection. No dose rows or final statuses are created or changed here. */
internal data class MedicationDayProgress(
    val total: Int, val taken: Int, val remaining: Int, val missed: Int, val unrecorded: Int,
    val nextAt: Long?, val skipped: Int = 0
)

internal fun medicationDayProgress(
    planned: List<Pair<Long, Long>>,
    doses: List<DoseLog>,
    activeMedicationIds: Set<Long>,
    now: Long,
    missedWindowMs: Long = MonitoringRepository.DEFAULT_MISSED_WINDOW_MS
): MedicationDayProgress {
    val recorded = doses.associateBy { it.medicationId to it.scheduledAt }
    val occurrences = (planned + recorded.keys).toSet()
    fun finalStatus(key: Pair<Long, Long>) = recorded[key]?.status
    val taken = occurrences.count { finalStatus(it) == DoseStatus.TAKEN }
    val missed = occurrences.count { finalStatus(it) == DoseStatus.MISSED }
    val skipped = occurrences.count { finalStatus(it) == DoseStatus.SKIPPED }
    val pending = occurrences.filter { finalStatus(it) !in setOf(DoseStatus.TAKEN, DoseStatus.MISSED, DoseStatus.SKIPPED) }
    // An overdue occurrence without a final database status is not an inferred MISSED record.
    val unrecorded = pending.count { it.second <= now - missedWindowMs }
    val next = pending.filter { it.first in activeMedicationIds && it.second >= now }.minOfOrNull { it.second }
    return MedicationDayProgress(occurrences.size, taken, pending.size - unrecorded, missed, unrecorded, next, skipped)
}
