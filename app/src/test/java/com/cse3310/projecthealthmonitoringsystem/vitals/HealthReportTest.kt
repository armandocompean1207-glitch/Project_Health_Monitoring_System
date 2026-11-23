package com.cse3310.projecthealthmonitoringsystem.vitals

import org.junit.Assert.*
import org.junit.Test

class HealthReportTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private fun reading(id: Long, at: Long, name: String = "Blood glucose", value: Double = 355.0) =
        VitalReadingItem(name, value.toString(), "mg/dL", recordedAt = "11/22/2026 · 10:40 AM",
            isAbnormal = true, timestamp = at, contextLabel = "After a meal", readingId = id, primaryNumeric = value)
    private val type = VitalTypeOption(1, "Blood glucose", "mg/dL")

    @Test fun reportUsesInclusivePeriodAndPreservesSavedValuesContextAndFlags() {
        val records = listOf(reading(1, now), reading(2, now - 7 * day), reading(3, now - 7 * day - 1),
            reading(4, now + 1), reading(5, now, "Pain Level"))
        val window = readingWindow(ReadingPeriod.WEEK, 0, 0, now)
        val report = healthReportSnapshot("owner", "Alex Patient", window, listOf(type), records, now)
        assertEquals(listOf(1L, 2L), report.readings.map { it.readingId })
        assertEquals("Alex Patient", report.patientName)
        assertEquals("owner", report.owner)
        assertEquals(355.0, report.readings.first().primaryNumeric!!, 0.0)
        assertEquals("mg/dL", report.readings.first().unit)
        assertEquals("After a meal", report.readings.first().contextLabel)
        assertTrue(report.readings.first().isAbnormal)
        assertEquals(now, report.generatedAt)
    }
    @Test fun allAndCustomWindowsNeverInventRecordsOrIncludeFutureReadings() {
        val records = listOf(reading(1, now - 400 * day), reading(2, now - 2 * day),
            reading(3, now), reading(4, now + 1))
        val all = healthReportSnapshot("owner", "", readingWindow(ReadingPeriod.ALL, 0, 0, now),
            listOf(type), records, now)
        assertEquals(listOf(3L, 2L, 1L), all.readings.map { it.readingId })
        val custom = readingWindow(ReadingPeriod.CUSTOM, now - 2 * day, now, now)
        assertEquals(listOf(3L, 2L), healthReportSnapshot("owner", "", custom, listOf(type), records, now)
            .readings.map { it.readingId })
        assertTrue(healthReportSnapshot("owner", "", custom, listOf(type), emptyList(), now).readings.isEmpty())
    }
    @Test fun reportSnapshotIsStableAndKeepsPreferredTemperatureUnits() {
        val records = mutableListOf(reading(1, now, "Body temperature", 97.0).copy(unit = "°F"))
        val report = healthReportSnapshot("owner", "Alex", readingWindow(ReadingPeriod.ALL, 0, 0, now),
            listOf(VitalTypeOption(2, "Body temperature", "°F")), records, now)
        records.clear()
        assertEquals("97.0 °F", readingText(report.readings.single()))
        assertEquals(97.0, report.readings.single().primaryNumeric!!, 0.0)
    }
    @Test fun vaccinationsAreOptInScopedAndFilteredByReceivedDate() {
        val dates = com.cse3310.projecthealthmonitoringsystem.data.repository.VaccinationDates
        val window = readingWindow(ReadingPeriod.WEEK, 0, 0, now)
        val data = listOf(
            com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination(1, "owner", "Fictional vaccine",
                dates.iso(now), doseNumber = 2, notes = "Keep context"),
            com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination(2, "other", "Private",
                dates.iso(now)),
            com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination(3, "owner", "Old",
                dates.iso(now - 20 * day)))
        assertTrue(healthReportSnapshot("owner", "Alex", window, listOf(type), emptyList(), now, data).vaccinations.isEmpty())
        val selected = healthReportSnapshot("owner", "Alex", window, listOf(type), emptyList(), now, data, true)
        assertEquals(listOf(1L), selected.vaccinations.map { it.vaccinationId })
        assertTrue(vaccinationReportLines(selected.vaccinations.single()).contains("Dose: 2"))
        assertTrue(vaccinationReportLines(selected.vaccinations.single()).contains("Notes: Keep context"))
    }
    @Test fun sparseHighGlucoseUsesReadableWholeNumberAxisIntervals() {
        val scale = roundedScale(listOf(reading(1, now)), "Blood glucose")
        assertTrue(scale.low < 355 && scale.high > 355)
        assertTrue(scale.high - scale.low >= 70)
        (0..4).forEach { index ->
            val tick = scale.high - index * (scale.high - scale.low) / 4
            assertEquals(kotlin.math.floor(tick), tick, 0.0)
        }
    }
}
