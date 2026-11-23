package com.cse3310.projecthealthmonitoringsystem.vitals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared presentation for stored readings; no interpolation or invented ranges. */
internal fun vitalChartColors(name: String): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val colors = when {
        name.contains("pressure", true) -> 0xFFE05260 to 0xFF4169D1
        name.contains("glucose", true) -> 0xFF3979D9 to 0xFF3979D9
        name.contains("temperature", true) -> 0xFFE88A32 to 0xFFE88A32
        name.contains("cholesterol", true) -> 0xFF8B5CD6 to 0xFF8B5CD6
        name.contains("heart", true) -> 0xFFDB508F to 0xFFDB508F
        else -> 0xFF169C85 to 0xFF169C85
    }
    return androidx.compose.ui.graphics.Color(colors.first) to androidx.compose.ui.graphics.Color(colors.second)
}

private fun glucoseGroup(item: VitalReadingItem) = when {
    item.contextLabel?.contains("fasting", true) == true -> 0
    item.contextLabel?.contains("before", true) == true -> 1
    item.contextLabel?.contains("after", true) == true -> 2
    else -> 3
}

private fun dayGroups(points: List<VitalReadingItem>) =
    points.groupBy { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.timestamp)) }.values

internal fun roundedScale(points: List<VitalReadingItem>, name: String = ""): ChartScale {
    val raw = chartScale(points)
    val values = points.flatMap { listOfNotNull(chartValue(it), chartValue(it, true)) }.filter(Double::isFinite)
    val center = values.average().takeIf { it.isFinite() } ?: 0.0
    // Keep sparse manual readings in a useful window instead of zooming to fractional units.
    val minimumSpan = when {
        name.contains("temperature", true) -> 2.0
        name.contains("oxygen", true) -> 4.0
        else -> maxOf(20.0, kotlin.math.abs(center) * .2)
    }
    val span = maxOf(raw.high - raw.low, minimumSpan)
    val desired = span / 4
    val magnitude = Math.pow(10.0, kotlin.math.floor(kotlin.math.log10(desired)))
    val fraction = desired / magnitude
    val step = (when { fraction <= 1 -> 1.0; fraction <= 2 -> 2.0; fraction <= 5 -> 5.0; else -> 10.0 }) * magnitude
    val low = kotlin.math.floor(minOf(raw.low, center - span / 2) / step) * step
    return raw.copy(low = low, high = maxOf(low + step * 4, kotlin.math.ceil(raw.high / step) * step))
}

/** Canvas reused by the dashboard previews and full trend pages. */
@Composable
internal fun VitalTrendPlot(points: List<VitalReadingItem>, type: VitalTypeOption,
    modifier: Modifier, selected: VitalReadingItem? = null, showGrid: Boolean = false,
    onSelect: ((VitalReadingItem) -> Unit)? = null) {
    val scale = roundedScale(points, type.name)
    val (primary, secondary) = vitalChartColors(type.name)
    val grid = androidx.compose.ui.graphics.Color(0xFFEDF0F2)
    val inset = with(LocalDensity.current) { 10.dp.toPx() }
    val heart = type.name.contains("heart", true)
    val glucose = type.name.contains("glucose", true)
    val shaded = type.name.contains("temperature", true) || type.name.contains("oxygen", true) || type.name.contains("pressure", true)
    Canvas(modifier.semantics {
        contentDescription = "${type.name}, ${points.size} saved readings, ${type.unit}."
    }.pointerInput(points, type.name, onSelect != null) {
        if (onSelect != null) detectTapGestures { tap ->
            val index = if (heart) {
                val group = dayGroups(points).minByOrNull { values ->
                    val p = chartPosition(values.first(), chartValue(values.first())!!, scale,
                        size.width.toFloat(), size.height.toFloat(), inset)
                    kotlin.math.abs(p.first - tap.x)
                }
                group?.minByOrNull {
                    kotlin.math.abs(chartPosition(it, chartValue(it)!!, scale,
                        size.width.toFloat(), size.height.toFloat(), inset).second - tap.y)
                }?.let { points.indexOf(it) }
            } else nearestChartReading(points, scale, size.width.toFloat(), size.height.toFloat(),
                inset, tap.x, tap.y)
            index?.let { onSelect(points[it]) }
        }
    }) {
        if (showGrid) {
            repeat(5) { row ->
                val y = inset + row * (size.height - inset * 2) / 4
                drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
            }
        }
        fun position(item: VitalReadingItem, value: Double) =
            chartPosition(item, value, scale, size.width, size.height, inset).let { Offset(it.first, it.second) }
        if (heart) {
            dayGroups(points).forEach { group ->
                val first = group.first()
                val low = group.minOf { chartValue(it)!! }
                val high = group.maxOf { chartValue(it)!! }
                val lower = position(first, low)
                val upper = position(first, high)
                if (low != high) drawLine(primary, lower, upper, 7.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                else drawCircle(primary, 3.5.dp.toPx(), lower)
                if (selected in group) {
                    val dot = position(first, chartValue(selected!!)!!)
                    drawCircle(androidx.compose.ui.graphics.Color.Black, 5.dp.toPx(), dot)
                    drawCircle(primary, 3.dp.toPx(), dot)
                }
            }
        } else if (glucose) {
            points.forEach { item ->
                val p = position(item, chartValue(item)!!)
                val radius = if (item == selected) 5.dp.toPx() else 3.5.dp.toPx()
                when (glucoseGroup(item)) {
                    1 -> drawRect(primary, Offset(p.x - radius, p.y - radius),
                        androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
                    2 -> {
                        val triangle = androidx.compose.ui.graphics.Path().apply {
                            moveTo(p.x, p.y - radius * 1.3f); lineTo(p.x + radius, p.y + radius)
                            lineTo(p.x - radius, p.y + radius); close()
                        }
                        drawPath(triangle, secondary)
                    }
                    else -> drawCircle(primary, radius, p)
                }
            }
        } else {
            listOf(false, true).forEach { second ->
                if (!second || type.secondValueLabel != null) {
                    val series = points.mapNotNull { item ->
                        chartValue(item, second)?.takeIf(Double::isFinite)?.let { item to position(item, it) }
                    }
                    val color = if (second) secondary else primary
                    if (shaded && !second && series.size > 1) {
                        val area = androidx.compose.ui.graphics.Path().apply {
                            moveTo(series.first().second.x, size.height - inset)
                            series.forEach { lineTo(it.second.x, it.second.y) }
                            lineTo(series.last().second.x, size.height - inset); close()
                        }
                        drawPath(area, androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(color.copy(alpha = .22f), color.copy(alpha = .01f))))
                    }
                    series.zipWithNext().forEach { (a, b) ->
                        drawLine(color, a.second, b.second, 3.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                    series.forEach { (item, p) ->
                        if (item == selected) drawCircle(androidx.compose.ui.graphics.Color.Black, 5.dp.toPx(), p)
                        drawCircle(color, if (item == selected) 3.5.dp.toPx() else 3.dp.toPx(), p)
                    }
                }
            }
        }
    }
}

@Composable
fun VitalHistoryChart(readings: List<VitalReadingItem>, type: VitalTypeOption) {
    var days by remember(type.id) { mutableIntStateOf(7) }
    var table by remember(type.id) { mutableStateOf(false) }
    var selectedId by remember(type.id, days) { mutableStateOf<Pair<Long, Long>?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); kotlinx.coroutines.delay(60_000) }
    }
    val points = chartReadings(readings, days, now)
    val scale = roundedScale(points, type.name)
    val (primary, secondary) = vitalChartColors(type.name)
    val selected = points.firstOrNull { (it.readingId to it.timestamp) == selectedId }
    val axisWidth = maxOf(40.dp, 30.dp * LocalDensity.current.fontScale)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(7, 30, 90).forEach { range ->
                FilterChip(selected = days == range, onClick = { days = range }, label = { Text("${range}D") })
            }
            FilterChip(selected = !table, onClick = { table = false }, label = { Text("Chart") })
            FilterChip(selected = table, onClick = { table = true }, label = { Text("Table") })
        }
        if (points.isEmpty()) Text("No readings in this period. Add a reading or choose a longer period.")
        else if (table) {
            points.asReversed().forEach { item ->
                ListItem(headlineContent = { Text(readingText(item)) },
                    supportingContent = { Text(listOfNotNull(item.recordedAt, item.contextLabel).joinToString("\n")) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.White))
                HorizontalDivider()
            }
        } else {
            Text(type.unit, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.width(axisWidth).height(220.dp).padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween) {
                    (0..4).forEach { i ->
                        Text(java.math.BigDecimal.valueOf(scale.high - i * (scale.high - scale.low) / 4)
                            .setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                VitalTrendPlot(points, type, Modifier.weight(1f).height(220.dp), selected, true) {
                    selectedId = it.readingId to it.timestamp
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = axisWidth + 18.dp, end = 10.dp),
                horizontalArrangement = if (scale.span == 0L) Arrangement.Center else Arrangement.SpaceBetween) {
                val format = SimpleDateFormat("MM/dd", Locale.US)
                Text(format.format(Date(points.first().timestamp)), style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color(0xFF111827))
                if (scale.span > 0L) Text(format.format(Date(points.last().timestamp)), style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color(0xFF111827))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    type.secondValueLabel != null -> {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Canvas(Modifier.size(10.dp)) { drawCircle(primary) }
                            Text("Systolic", color = androidx.compose.ui.graphics.Color(0xFF111827), style = MaterialTheme.typography.labelLarge)
                        }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Canvas(Modifier.size(10.dp)) { drawCircle(secondary) }
                            Text("Diastolic", color = androidx.compose.ui.graphics.Color(0xFF111827), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    type.name.contains("glucose", true) -> {
                        listOf("●" to "Fasting", "■" to "Before meal", "▲" to "After meal", "●" to "Other / unspecified").forEach { (marker, label) ->
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(marker, color = primary,
                                    style = MaterialTheme.typography.labelLarge)
                                Text(label, color = androidx.compose.ui.graphics.Color(0xFF111827), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    type.name.contains("heart", true) -> Text("Daily recorded range · single reading shown as a point",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            selected?.let { item ->
                Text(readingText(item), style = MaterialTheme.typography.headlineMedium)
                Text(item.recordedAt, style = MaterialTheme.typography.bodyMedium)
                readingComparison(readings, item)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                item.contextLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (type.name.contains("heart", true)) {
                    val day = dayGroups(points).first { item in it }
                    if (day.size > 1) Text(
                        "Recorded daily range: ${day.minOf { chartValue(it)!! }}–${day.maxOf { chartValue(it)!! }} ${type.unit} · ${day.size} readings",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (item.isAbnormal) Text("Health alert", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge)
                val index = points.indexOf(item)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    PhmsTextButton(enabled = index > 0, onClick = { selectedId = points[index - 1].readingId to points[index - 1].timestamp }) { Text("Previous") }
                    PhmsTextButton(enabled = index < points.lastIndex, onClick = { selectedId = points[index + 1].readingId to points[index + 1].timestamp }) { Text("Next") }
                }
            }
            if (selected == null && points.size > 1) PhmsTextButton(onClick = {
                selectedId = points.last().readingId to points.last().timestamp
            }) { Text("Inspect readings") }
        }
    }
}

internal fun readingText(item: VitalReadingItem) =
    "${item.value}${item.secondValue?.let { " / $it" } ?: ""} ${item.unit}"
