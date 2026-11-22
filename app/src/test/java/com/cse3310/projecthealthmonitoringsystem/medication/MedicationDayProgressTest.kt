package com.cse3310.projecthealthmonitoringsystem.medication

import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import org.junit.Assert.*
import org.junit.Test

class MedicationDayProgressTest {
    private val hour = 3_600_000L
    private val now = 12 * hour
    private fun dose(med: Long, at: Long, status: DoseStatus) =
        DoseLog(userId = "fictional", medicationId = med, scheduledAt = at, status = status)

    @Test fun fourDailyOccurrencesOfOneMedicineAreFourDoses() {
        val plan = listOf(8L, 9L, 10L, 13L).map { 1L to it * hour }
        val records = listOf(dose(1, 8 * hour, DoseStatus.TAKEN), dose(1, 9 * hour, DoseStatus.TAKEN),
            dose(1, 10 * hour, DoseStatus.MISSED), dose(1, 13 * hour, DoseStatus.SCHEDULED))
        val day = medicationDayProgress(plan, records, setOf(1), now)
        assertEquals(4, day.total)
        assertEquals(2, day.taken)
        assertEquals(1, day.missed)
        assertEquals(1, day.remaining)
        assertEquals(0, day.unrecorded)
        assertEquals(13 * hour, day.nextAt)
    }

    @Test fun takingPendingDoseUpdatesCompletionWithoutDoubleCountingPlannedAndStoredRows() {
        val plan = listOf(1L to 12 * hour)
        val before = medicationDayProgress(plan, listOf(dose(1, 12 * hour, DoseStatus.SCHEDULED)), setOf(1), now)
        val after = medicationDayProgress(plan, listOf(dose(1, 12 * hour, DoseStatus.TAKEN)), setOf(1), now)
        assertEquals(1, before.total)
        assertEquals(0, before.taken)
        assertEquals(1, before.remaining)
        assertEquals(1, after.total)
        assertEquals(1, after.taken)
        assertEquals(0, after.remaining)
        assertNull(after.nextAt)
    }

    @Test fun missingOrPendingOverdueHistoryIsNeverInventedAsMissed() {
        val day = medicationDayProgress(listOf(1L to 8 * hour, 1L to 9 * hour, 1L to 14 * hour),
            listOf(dose(1, 9 * hour, DoseStatus.SCHEDULED)), setOf(1), now)
        assertEquals(3, day.total)
        assertEquals(0, day.missed)
        assertEquals(2, day.unrecorded)
        assertEquals(1, day.remaining)
        assertEquals(day.total, day.taken + day.remaining + day.missed + day.unrecorded)
    }

    @Test fun finalRecordsOfDeactivatedMedicinesStayInTodayTotalsButNotNextReminder() {
        val day = medicationDayProgress(listOf(2L to 14 * hour),
            listOf(dose(1, 8 * hour, DoseStatus.TAKEN), dose(1, 10 * hour, DoseStatus.MISSED),
                dose(2, 14 * hour, DoseStatus.SCHEDULED)), setOf(2), now)
        assertEquals(3, day.total)
        assertEquals(1, day.taken)
        assertEquals(1, day.missed)
        assertEquals(14 * hour, day.nextAt)
    }

    @Test fun noOccurrencesHasNoZeroOverZeroProgressOrNextDose() {
        val day = medicationDayProgress(emptyList(), emptyList(), emptySet(), now)
        assertEquals(0, day.total)
        assertEquals(0, day.remaining)
        assertNull(day.nextAt)
    }
}
