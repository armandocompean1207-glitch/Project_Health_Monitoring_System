package com.cse3310.projecthealthmonitoringsystem.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun parseUsDate(text: String): Long? {
    if (!Regex("\\d{2}/\\d{2}/\\d{4}").matches(text)) return null
    val position = ParsePosition(0)
    return SimpleDateFormat("MM/dd/yyyy", Locale.US).apply { isLenient = false }.parse(text, position)?.time
        ?.takeIf { position.index == text.length }
}
internal fun dateDay(time: Long): Long = Calendar.getInstance().apply {
    timeInMillis = time; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis
internal fun dateAllowed(time: Long, minDate: Long?, maxDate: Long?) =
    (minDate == null || dateDay(time) >= dateDay(minDate)) && (maxDate == null || dateDay(time) <= dateDay(maxDate))
internal fun calendarDate(year: Int, month: Int, day: Int): Long? = runCatching {
    require(year in 1..9999 && month in 0..11)
    Calendar.getInstance().apply { clear(); isLenient = false; set(year, month, day) }.timeInMillis
}.getOrNull()
internal fun combineDateTime(date: Long, previous: Long?): Long {
    val clock = Calendar.getInstance().apply { timeInMillis = previous ?: System.currentTimeMillis() }
    return Calendar.getInstance().apply {
        timeInMillis = date
        set(Calendar.HOUR_OF_DAY, clock.get(Calendar.HOUR_OF_DAY)); set(Calendar.MINUTE, clock.get(Calendar.MINUTE))
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

@Composable
fun PhmsDateInput(value: Long?, onDateChange: (Long?) -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, required: Boolean = true,
    minDate: Long? = null, maxDate: Long? = null, useStandardFieldStyle: Boolean = false, onValidityChange: (Boolean) -> Unit = {}) {
    val format = remember { SimpleDateFormat("MM/dd/yyyy", Locale.US) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    var text by remember { mutableStateOf(value?.let { format.format(Date(it)) }.orEmpty()) }
    var emitted by remember { mutableStateOf(value) }
    var touched by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (value != emitted) { text = value?.let { format.format(Date(it)) }.orEmpty(); emitted = value; touched = false }
    }
    val parsed = parseUsDate(text)
    val valid = if (text.isBlank()) !required else parsed != null && dateAllowed(parsed, minDate, maxDate)
    val showError = !valid && (touched || value != null)
    SideEffect { onValidityChange(!enabled || valid) }
    PhmsTextField(text, { draft ->
        text = draft; touched = true
        val date = parseUsDate(draft)
        val acceptable = if (draft.isBlank()) !required else date != null && dateAllowed(date, minDate, maxDate)
        onValidityChange(acceptable)
        if (acceptable) {
            val selected = date?.let { combineDateTime(it, value) }
            emitted = selected
            onDateChange(selected)
        }
    }, modifier = modifier.fillMaxWidth(), enabled = enabled, singleLine = true,
        label = { Text(label, color = if (useStandardFieldStyle) Color.Unspecified else Color(0xFF111827)) },
        placeholder = { Text("MM/DD/YYYY", color = if (useStandardFieldStyle) Color.Unspecified else Color(0xFF111827)) },
        colors = if (useStandardFieldStyle) OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.onSurface, unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.onSurface)
        else OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
            errorContainerColor = Color.White, focusedTextColor = Color(0xFF111827), unfocusedTextColor = Color(0xFF111827),
            focusedBorderColor = Color(0xFFFFDF48), unfocusedBorderColor = Color(0xFFE8E1D8)),
        isError = showError,
        supportingText = { if (showError) Text(
            if (parsed == null) "Enter a valid date as MM/DD/YYYY." else "Choose a date within the allowed range.") },
        trailingIcon = {
            IconButton(enabled = enabled, onClick = { focus.clearFocus(); keyboard?.hide(); choosing = true }) {
                Icon(Icons.Outlined.CalendarMonth, "Choose $label", tint = Color(0xFF111827))
            }
        })
    val initialDate = parsed ?: value ?: System.currentTimeMillis()
    val pickerInitial = when {
        minDate != null && dateDay(initialDate) < dateDay(minDate) -> minDate
        maxDate != null && dateDay(initialDate) > dateDay(maxDate) -> maxDate
        else -> initialDate
    }
    if (choosing) PhmsCalendarPicker(pickerInitial, minDate, maxDate,
        onDismiss = { choosing = false }, onConfirm = { date ->
            val selected = combineDateTime(date, value)
            text = format.format(Date(selected)); emitted = selected; touched = false
            onValidityChange(true); onDateChange(selected); choosing = false
        })
}

@Composable
private fun PhmsCalendarPicker(initial: Long, minDate: Long?, maxDate: Long?, onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit) {
    val initialCalendar = remember { Calendar.getInstance().apply { timeInMillis = initial } }
    var year by remember { mutableIntStateOf(initialCalendar.get(Calendar.YEAR).coerceIn(1, 9999)) }
    var month by remember { mutableIntStateOf(initialCalendar.get(Calendar.MONTH)) }
    var day by remember { mutableIntStateOf(initialCalendar.get(Calendar.DAY_OF_MONTH)) }
    var step by remember { mutableStateOf("Day") }
    var decade by remember { mutableIntStateOf(year / 10 * 10) }
    var jump by remember { mutableStateOf(year.toString()) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val yellow = Color(0xFFFFDF48)
    val ink = Color(0xFF111827)
    fun monthEnd(y: Int, m: Int): Long = Calendar.getInstance().apply {
        clear(); set(y, m, 1); set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
    }.timeInMillis
    fun monthAllowed(y: Int, m: Int): Boolean {
        val first = calendarDate(y, m, 1) ?: return false
        return (maxDate == null || first <= dateDay(maxDate)) && (minDate == null || monthEnd(y, m) >= dateDay(minDate))
    }
    fun clampDay() {
        day = minOf(day, Calendar.getInstance().apply { clear(); set(year, month, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onSurface = ink, onSurfaceVariant = ink,
            onBackground = ink, surface = Color.White, primary = yellow, onPrimary = ink)) {
        Surface(Modifier.padding(12.dp).fillMaxWidth().widthIn(max = 520.dp),
            shape = RoundedCornerShape(20.dp), color = Color.White, contentColor = ink,
            border = BorderStroke(1.dp, Color(0xFFE8E1D8))) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose a date", style = MaterialTheme.typography.titleLarge, color = ink)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PhmsOutlinedButton(onClick = { decade = year / 10 * 10; jump = year.toString(); step = "Year" }) { Text("Year: $year") }
                    PhmsOutlinedButton(onClick = { step = "Month" }) { Text(SimpleDateFormat("MMMM", Locale.US).format(Date(calendarDate(year, month, 1)!!))) }
                }
                when (step) {
                    "Year" -> {
                        PhmsTextField(jump, { input ->
                            jump = input.filter(Char::isDigit).take(4)
                            jump.toIntOrNull()?.takeIf { jump.length == 4 && it in 1..9999 }?.let { decade = it / 10 * 10 }
                        }, label = { Text("Jump to year") }, singleLine = true)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton(onClick = { decade = (decade - 10).coerceAtLeast(0) }, enabled = decade > 0) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous decade")
                            }
                            Text("$decade–${minOf(9999, decade + 9)}", color = ink)
                            IconButton(onClick = { decade = (decade + 10).coerceAtMost(9990) }, enabled = decade < 9990) {
                                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next decade")
                            }
                        }
                        (decade..decade + 9).toList().chunked(2).forEach { years ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                years.forEach { option ->
                                    val available = option in 1..9999 && (0..11).any { monthAllowed(option, it) }
                                    Surface(onClick = { focus.clearFocus(); keyboard?.hide(); year = option; clampDay(); step = "Month" }, enabled = available,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                                        color = if (year == option) yellow else Color.White,
                                        border = BorderStroke(1.dp, Color(0xFFE8E1D8))) {
                                        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                            Text(if (option == 0) "—" else option.toString(), color = if (available) ink else ink.copy(alpha = .35f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "Month" -> (0..11).toList().chunked(3).forEach { months ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            months.forEach { option ->
                                Surface(onClick = { focus.clearFocus(); keyboard?.hide(); month = option; clampDay(); step = "Day" }, enabled = monthAllowed(year, option),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
                                    color = if (month == option) yellow else Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFE8E1D8))) {
                                    Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                        Text(SimpleDateFormat("MMM", Locale.US).format(Date(calendarDate(year, option, 1)!!)), color = ink)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val first = Calendar.getInstance().apply { clear(); set(year, month, 1) }
                        val offset = first.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
                        val count = first.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val cell = maxOf(48.dp, 30.dp * LocalDensity.current.fontScale)
                        Column(Modifier.horizontalScroll(rememberScrollState()).width(cell * 7)) {
                            Row { listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { Text(it, Modifier.width(cell), color = ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
                            val cells = ((0 until offset).map { 0 } + (1..count)).toMutableList()
                            while (cells.size % 7 != 0) cells.add(0)
                            cells.chunked(7).forEach { week ->
                                Row {
                                    week.forEach { option ->
                                        if (option == 0) Spacer(Modifier.size(cell))
                                        else {
                                            val time = calendarDate(year, month, option)!!
                                            val allowed = dateAllowed(time, minDate, maxDate)
                                            Surface(onClick = { day = option }, enabled = allowed, modifier = Modifier.size(cell),
                                                shape = RoundedCornerShape(24.dp), color = if (day == option) yellow else Color.White) {
                                                Box(contentAlignment = Alignment.Center) { Text(option.toString(), color = if (allowed) ink else ink.copy(alpha = .35f)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PhmsOutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    val selected = calendarDate(year, month, day)
                    Button(enabled = selected != null && dateAllowed(selected, minDate, maxDate),
                        onClick = { selected?.let(onConfirm) },
                        colors = ButtonDefaults.buttonColors(containerColor = yellow, contentColor = ink)) { Text("Use date") }
                }
            }
        }
        }
    }
}
