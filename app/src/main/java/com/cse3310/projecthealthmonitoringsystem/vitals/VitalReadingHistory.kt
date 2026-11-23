package com.cse3310.projecthealthmonitoringsystem.vitals

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal enum class ReadingPeriod(val label: String, val days: Int?) {
    WEEK("Last 7 days", 7), MONTH("Last 30 days", 30), QUARTER("Last 90 days", 90),
    ALL("All records", null), CUSTOM("Custom date range", null)
}
internal data class ReadingWindow(val start: Long, val end: Long, val label: String) {
    fun contains(item: VitalReadingItem) = item.timestamp in start..end
}
internal fun readingWindow(period: ReadingPeriod, from: Long, to: Long, now: Long): ReadingWindow {
    val start = when (period) {
        ReadingPeriod.CUSTOM -> from
        ReadingPeriod.ALL -> Long.MIN_VALUE
        else -> now - period.days!! * 86_400_000L
    }
    val end = if (period == ReadingPeriod.CUSTOM) minOf(to, now) else now
    val date = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    val label = if (period == ReadingPeriod.ALL) "All records through ${date.format(Date(end))}"
        else "${date.format(Date(start))} – ${date.format(Date(end))}"
    return ReadingWindow(start, end, label)
}
internal fun dayBoundary(time: Long, end: Boolean = false): Long =
    Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, if (end) 23 else 0)
        set(Calendar.MINUTE, if (end) 59 else 0)
        set(Calendar.SECOND, if (end) 59 else 0)
        set(Calendar.MILLISECOND, if (end) 999 else 0)
    }.timeInMillis

@Composable
internal fun ReadingRangeSelector(period: ReadingPeriod, from: Long, to: Long,
    onValidityChange: (Boolean) -> Unit = {}, onChange: (ReadingPeriod, Long, Long) -> Unit) {
    val context = LocalContext.current
    var fromValid by remember { mutableStateOf(true) }
    var toValid by remember { mutableStateOf(true) }
    SideEffect { onValidityChange(period != ReadingPeriod.CUSTOM || (fromValid && toValid && from <= to)) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReadingPeriod.entries.forEach { choice ->
            FilterChip(selected = period == choice, onClick = { onChange(choice, from, to) },
                label = { Text(choice.label, color = Color(0xFF111827)) })
        }
    }
    if (period == ReadingPeriod.CUSTOM) {
        PhmsDateInput(from, { it?.let { time -> onChange(period, dayBoundary(time), to) } },
            "From", maxDate = System.currentTimeMillis(), onValidityChange = { fromValid = it })
        PhmsDateInput(to, { it?.let { time -> onChange(period, from, dayBoundary(time, true)) } },
            "To", minDate = from, maxDate = System.currentTimeMillis(), onValidityChange = { toValid = it })
        if (from > to) Text("Choose an end date on or after the start date.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun ReadingHistoryRows(readings: List<VitalReadingItem>) {
    readings.forEach { item ->
        ListItem(headlineContent = { Text(readingText(item), color = Color(0xFF111827)) },
            supportingContent = { Text(listOfNotNull(item.recordedAt, item.contextLabel).joinToString("\n"), color = Color(0xFF111827)) },
            trailingContent = { if (item.isAbnormal) Icon(Icons.Outlined.WarningAmber, "Abnormal reading", tint = MaterialTheme.colorScheme.error) },
            colors = ListItemDefaults.colors(containerColor = Color.White))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
@Composable
internal fun ReadingHistoryPreview(type: VitalTypeOption, readings: List<VitalReadingItem>, onSeeAll: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Reading history", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        PhmsTextButton(onClick = onSeeAll) { Text("See all") }
    }
    if (readings.isEmpty()) Text("Add your first ${type.name.lowercase()} reading to start your history.")
    ReadingHistoryRows(readings.sortedWith(compareByDescending<VitalReadingItem> { it.timestamp }.thenByDescending { it.readingId }).take(5))
}
@Composable
internal fun VitalReadingHistory(type: VitalTypeOption, readings: List<VitalReadingItem>, onBack: () -> Unit) {
    var period by remember(type.id) { mutableStateOf(ReadingPeriod.ALL) }
    var from by remember(type.id) { mutableLongStateOf(dayBoundary(System.currentTimeMillis() - 6 * 86_400_000L)) }
    var to by remember(type.id) { mutableLongStateOf(dayBoundary(System.currentTimeMillis(), true)) }
    PhmsPage {
        PhmsScreenHeader("${type.name} history", icon = vitalIconFor(type.name), onBack = onBack)
        ReadingRangeSelector(period, from, to) { p, f, t -> period = p; from = f; to = t }
        val window = readingWindow(period, from, to, System.currentTimeMillis())
        val visible = readings.filter(window::contains).sortedWith(
            compareByDescending<VitalReadingItem> { it.timestamp }.thenByDescending { it.readingId })
        Text(window.label, style = MaterialTheme.typography.bodyMedium)
        if (visible.isEmpty()) Text("No readings in this period. Choose a different date range.")
        ReadingHistoryRows(visible)
    }
}
