package com.cse3310.projecthealthmonitoringsystem.vitals

import com.cse3310.projecthealthmonitoringsystem.ui.components.measurementComparison
import org.junit.Assert.*
import org.junit.Test

class MeasurementComparisonTest {
    @Test fun phrasesAreNeutralAndRemoveOnlyUnneededZeros() {
        assertEquals("5 lbs higher than last time", measurementComparison(175.0, 170.0, "lbs"))
        assertEquals("3 lbs lower than last time", measurementComparison(167.0, 170.0, "lbs"))
        assertEquals("8 bpm higher than last time", measurementComparison(78.0, 70.0, "bpm"))
        assertEquals("No change since last time", measurementComparison(70.0, 70.0, "bpm"))
        assertEquals("1.25 kg lower than last time", measurementComparison(68.75, 70.0, "kg"))
        assertNull(measurementComparison(Double.NaN, 70.0, "kg"))
    }
    private fun item(id: Long, time: Long, value: Double) = VitalReadingItem(
        "Heart Rate", value.toString(), "bpm", recordedAt = "date", isAbnormal = false,
        timestamp = time, readingId = id, primaryNumeric = value)
    @Test fun comparisonsUseTheImmediatelyPrecedingChronologicalReading() {
        val oldest = item(1, 1000, 60.0)
        val middle = item(2, 2000, 70.0)
        val latest = item(3, 3000, 78.0)
        val shuffled = listOf(middle, latest, oldest)
        assertEquals(latest, latestRecordedReading(shuffled))
        assertEquals("8 bpm higher than last time", readingComparison(shuffled, latest))
        assertEquals("10 bpm higher than last time", readingComparison(shuffled, middle))
        assertNull(readingComparison(shuffled, oldest))
        assertEquals(latest, latestRecordedReading(listOf(middle.copy(timestamp = 3000), latest)))
        assertNull(readingComparison(listOf(middle.copy(unit = "other"), latest), latest))
    }
}
