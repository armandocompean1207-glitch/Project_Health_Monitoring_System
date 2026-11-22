package com.cse3310.projecthealthmonitoringsystem.vitals

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as OutlinedCard
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment

/** Draws stored readings on a timestamp axis; a single reading is rendered as a point. */
@Composable
fun VitalHistoryChart(readings: List<VitalReadingItem>, type: VitalTypeOption) {
    var days by remember { mutableIntStateOf(7) }
    var selectedTime by remember(type.id, days) { mutableStateOf<Long?>(null) }
    val cutoff = System.currentTimeMillis() - days * 86_400_000L
    val points = readings.filter { it.timestamp >= cutoff && it.value.toDoubleOrNull()?.isFinite() == true }
        .sortedBy { it.timestamp }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(7, 30, 90).forEach { range ->
                FilterChip(selected = days == range, onClick = { days = range }, label = { Text("${range}D") })
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("History · ${type.unit}", style = MaterialTheme.typography.titleMedium)
                if (points.isEmpty()) {
                    Text("No readings in this period. Use Add reading to record one.",
                        style = MaterialTheme.typography.bodyMedium)
                } else {
                    val values = points.flatMap { listOfNotNull(it.value.toDoubleOrNull(), it.secondValue?.toDoubleOrNull()) }
                    val low = (values.minOrNull() ?: 0.0) - 5
                    val high = (values.maxOrNull() ?: 1.0) + 5
                    val first = points.first().timestamp
                    val span = (points.last().timestamp - first).coerceAtLeast(1)
                    Text("${"%.0f".format(low)}–${"%.0f".format(high)} ${type.unit}",
                        style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.widthIn(min = 32.dp).height(180.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        (0..4).forEach { index -> Text(String.format(Locale.US, "%.0f", high - index * (high - low) / 4),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Canvas(Modifier.weight(1f).height(180.dp)
                        .semantics { contentDescription = "Historical ${type.name}, ${points.size} readings. Select a reading in the history list below for accessible values." }
                        .pointerInput(points) {
                            detectTapGestures { tap ->
                                val target = ((tap.x - 12f) / (size.width - 24f)).coerceIn(0f, 1f)
                                selectedTime = points.minByOrNull { abs((it.timestamp - first).toDouble() / span - target) }?.timestamp
                            }
                        }) {
                        val inset = 12f
                        fun position(item: VitalReadingItem, value: Double) = Offset(
                            if (points.size == 1) size.width / 2 else inset + ((item.timestamp - first).toDouble() / span).toFloat() * (size.width - inset * 2),
                            size.height - inset - ((value - low) / (high - low)).toFloat() * (size.height - inset * 2))
                        repeat(5) { row ->
                            val y = inset + row * (size.height - inset * 2) / 4
                            drawLine(gridColor, Offset(inset, y), Offset(size.width - inset, y), 1f)
                        }
                        listOf(false, true).forEach { second ->
                            val series = points.mapNotNull { item ->
                                val value = (if (second) item.secondValue else item.value)?.toDoubleOrNull()
                                value?.takeIf { it.isFinite() }?.let { item to position(item, it) }
                            }
                            val color = if (second) secondary else primary
                            series.zipWithNext().forEach { (a, b) -> drawLine(color, a.second, b.second, 4f) }
                            series.forEach { (item, offset) ->
                                drawCircle(color, if (item.timestamp == selectedTime) 8f else 5f, offset)
                            }
                        }
                    }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val format = SimpleDateFormat("MMM d", Locale.US)
                        Text(format.format(Date(points.first().timestamp)), style = MaterialTheme.typography.bodySmall)
                        if (points.size > 1) Text(format.format(Date(points.last().timestamp)), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (type.secondValueLabel != null) "Systolic · Yellow    Diastolic · Slate" else type.name,
                        style = MaterialTheme.typography.bodySmall)
                    val selected = points.firstOrNull { it.timestamp == selectedTime } ?: points.last()
                    Text("${selected.value}${selected.secondValue?.let { " / $it" } ?: ""} ${selected.unit}",
                        style = MaterialTheme.typography.headlineMedium)
                    Text(selected.recordedAt, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val index = points.indexOf(selected)
                        com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton(
                            enabled = index > 0, onClick = { selectedTime = points[index - 1].timestamp }) { Text("Previous") }
                        com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton(
                            enabled = index < points.lastIndex, onClick = { selectedTime = points[index + 1].timestamp }) { Text("Next") }
                    }
                    Text(if (points.size == 1) "One reading in this period." else "Tap the chart or use Previous and Next.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
