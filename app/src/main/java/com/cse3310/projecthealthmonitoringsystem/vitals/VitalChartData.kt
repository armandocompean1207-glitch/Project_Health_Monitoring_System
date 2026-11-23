package com.cse3310.projecthealthmonitoringsystem.vitals

import kotlin.math.max

internal fun latestRecordedReading(readings: List<VitalReadingItem>) =
    readings.maxWithOrNull(compareBy<VitalReadingItem> { it.timestamp }.thenBy { it.readingId })

internal fun readingComparison(readings: List<VitalReadingItem>, current: VitalReadingItem): String? {
    val history = readings.filter { it.typeName == current.typeName }
        .sortedWith(compareBy<VitalReadingItem> { it.timestamp }.thenBy { it.readingId })
    val index = history.indexOf(current)
    val previous = history.getOrNull(index - 1) ?: return null
    if (current.unit != previous.unit) return null
    val value = chartValue(current) ?: return null
    val before = chartValue(previous) ?: return null
    val precision = if (current.typeName.contains("temperature", true)) 1 else 2
    val primary = com.cse3310.projecthealthmonitoringsystem.ui.components.measurementComparison(value, before, current.unit, precision) ?: return null
    val second = chartValue(current, true)
    val previousSecond = chartValue(previous, true)
    return if (second != null && previousSecond != null)
        "Systolic: $primary\nDiastolic: ${com.cse3310.projecthealthmonitoringsystem.ui.components.measurementComparison(second, previousSecond, current.unit, precision)}"
    else primary
}

internal fun supportedVital(name: String) = name.trim().lowercase(java.util.Locale.US) in setOf(
    "blood pressure", "blood glucose", "body temperature", "cholesterol", "heart rate", "oxygen saturation")

internal fun chartReadings(readings: List<VitalReadingItem>, days: Int, now: Long): List<VitalReadingItem> =
    readings.filter { it.timestamp in (now - days * 86_400_000L)..now && chartValue(it)?.isFinite() == true }
        .sortedWith(compareBy<VitalReadingItem> { it.timestamp }.thenBy { it.readingId })

internal fun chartValue(item: VitalReadingItem, secondary: Boolean = false): Double? =
    if (secondary) item.secondaryNumeric ?: item.secondValue?.toDoubleOrNull()
    else item.primaryNumeric ?: item.value.toDoubleOrNull()

internal data class ChartScale(val low: Double, val high: Double, val first: Long, val span: Long)
internal fun chartScale(points: List<VitalReadingItem>): ChartScale {
    val values = points.flatMap { listOfNotNull(chartValue(it), chartValue(it, true)).filter(Double::isFinite) }
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 1.0
    val padding = max((max - min) * .12, .5)
    return ChartScale(min - padding, max + padding, points.firstOrNull()?.timestamp ?: 0L,
        ((points.lastOrNull()?.timestamp ?: 0L) - (points.firstOrNull()?.timestamp ?: 0L)).coerceAtLeast(0L))
}
internal fun chartPosition(item: VitalReadingItem, value: Double, scale: ChartScale,
    width: Float, height: Float, inset: Float): Pair<Float, Float> =
    (if (scale.span == 0L) width / 2 else inset + ((item.timestamp - scale.first).toDouble() / scale.span).toFloat() * (width - 2 * inset).coerceAtLeast(1f)) to
        (height - inset - ((value - scale.low) / (scale.high - scale.low)).toFloat() * (height - 2 * inset).coerceAtLeast(1f))

internal fun nearestChartReading(points: List<VitalReadingItem>, scale: ChartScale,
    width: Float, height: Float, inset: Float, x: Float, y: Float): Int? =
    points.indices.minByOrNull { index ->
        listOfNotNull(chartValue(points[index]), chartValue(points[index], true)).filter(Double::isFinite).minOf { v ->
            val p = chartPosition(points[index], v, scale, width, height, inset)
            (p.first - x) * (p.first - x) + (p.second - y) * (p.second - y)
        }
    }
