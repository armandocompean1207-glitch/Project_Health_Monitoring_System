package com.cse3310.projecthealthmonitoringsystem.vitals

import org.junit.Assert.*
import org.junit.Test

class VitalChartDataTest {
    private val now = 1_800_000_000_000L
    private fun point(id: Long, at: Long, value: Double, second: Double? = null) =
        VitalReadingItem("Blood Pressure", value.toString(), "mmHg", second?.toString(), "date", false, at,
            readingId = id, primaryNumeric = value, secondaryNumeric = second)

    @Test fun onlySixMeasurementsAreSelectableWithoutRemovingLegacyData() {
        val names = listOf("Blood Pressure", "Blood Glucose", "Body Temperature", "Cholesterol", "Heart Rate", "Oxygen Saturation",
            "Pain Level", "Respiratory Rate")
        assertEquals(6, names.filter(::supportedVital).size)
        assertFalse(supportedVital("Pain Level"))
        assertFalse(supportedVital("Respiratory Rate"))
        assertTrue(supportedVital(" Body Temperature "))
    }
    @Test fun filtersHaveInclusiveBoundariesAndExcludeFutureAndInvalidValues() {
        val day = 86_400_000L
        val data = listOf(point(1, now, 120.0), point(2, now - 7 * day, 121.0),
            point(3, now - 30 * day, 122.0), point(4, now - 90 * day, 123.0),
            point(5, now + 1, 124.0), point(6, now, Double.NaN))
        assertEquals(2, chartReadings(data, 7, now).size)
        assertEquals(3, chartReadings(data, 30, now).size)
        assertEquals(4, chartReadings(data, 90, now).size)
        assertTrue(chartReadings(emptyList(), 7, now).isEmpty())
    }
    @Test fun singlePointHasFiniteScaleAndTapsSelectIt() {
        val points = listOf(point(1, now, 98.0))
        val scale = chartScale(points)
        assertTrue(scale.low < 98 && scale.high > 98)
        assertEquals(0, nearestChartReading(points, scale, 300f, 180f, 12f, 150f, 90f))
        assertEquals(150f, chartPosition(points.first(), 98.0, scale, 300f, 180f, 12f).first)
    }
    @Test fun tapsInspectEitherBloodPressureSeriesAndDistinguishEqualTimestamps() {
        val points = listOf(point(1, now - 1000, 120.0, 80.0), point(2, now, 160.0, 90.0), point(3, now, 145.0, 70.0))
        val scale = chartScale(points)
        for (index in points.indices) for (second in listOf(false, true)) {
            val p = chartPosition(points[index], chartValue(points[index], second)!!, scale, 300f, 180f, 12f)
            assertEquals(index, nearestChartReading(points, scale, 300f, 180f, 12f, p.first, p.second))
        }
    }
    @Test fun plottedValuesUseSavedPrecisionInsteadOfRoundedLabels() {
        val item = point(1, now, 101.2345).copy(value = "101.2")
        assertEquals(101.2345, chartValue(item)!!, 0.0)
    }
}
