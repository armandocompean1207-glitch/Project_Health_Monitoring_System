package com.cse3310.projecthealthmonitoringsystem.medication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsOutlinedButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsIcons
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun MedicationTreatmentDates(start: Long, end: Long?, ongoing: Boolean, enabled: Boolean,
    onStart: (Long) -> Unit, onEnd: (Long) -> Unit, onOngoing: (Boolean) -> Unit, showErrors: Boolean) {
    val context = LocalContext.current
    val format = SimpleDateFormat("MMM d, yyyy", Locale.US)
    fun pick(initial: Long, endOfDay: Boolean, selected: (Long) -> Unit) {
        val cal = Calendar.getInstance().apply { timeInMillis = initial }
        DatePickerDialog(context, { _, year, month, day ->
            val chosen = Calendar.getInstance().apply { clear(); set(year, month, day) }.timeInMillis
            selected(if (endOfDay) MedicationTimes.dayEnd(chosen) else MedicationTimes.dayStart(chosen))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }
    PhmsOutlinedButton(enabled = enabled, modifier = Modifier.fillMaxWidth(),
        onClick = { pick(start, false, onStart) }) {
        Icon(PhmsIcons.Appointments, null)
        Text("Start date · ${format.format(Date(start))}", Modifier.padding(start = 8.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = ongoing, onCheckedChange = onOngoing, enabled = enabled)
        Text("Ongoing medication", style = MaterialTheme.typography.bodyLarge)
    }
    PhmsOutlinedButton(enabled = enabled && !ongoing, modifier = Modifier.fillMaxWidth(),
        onClick = { pick(end ?: start, true, onEnd) }) {
        Icon(PhmsIcons.Appointments, null)
        Text(if (ongoing) "End date · Ongoing" else end?.let { "End date · ${format.format(Date(it))}" } ?: "Choose end date",
            Modifier.padding(start = 8.dp))
    }
    if (showErrors && !ongoing && (end == null || end < start)) {
        Text(if (end == null) "Choose an end date." else "End date cannot be before start date.",
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun MedicationDoseTimeFields(times: List<Int?>, enabled: Boolean, showErrors: Boolean,
    onTime: (Int, Int) -> Unit) {
    val context = LocalContext.current
    times.forEachIndexed { index, minutes ->
        PhmsOutlinedButton(enabled = enabled, modifier = Modifier.fillMaxWidth(), onClick = {
            val cal = Calendar.getInstance()
            val initial = minutes ?: (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE))
            TimePickerDialog(context, { _, hour, minute -> onTime(index, hour * 60 + minute) },
                initial / 60, initial % 60, false).show()
        }) {
            Text("Dose ${index + 1} · " + (minutes?.let(DoseTimes::displayClock) ?: "Choose time"))
        }
        if (showErrors && (minutes == null || times.count { it == minutes } > 1)) {
            Text(if (minutes == null) "Choose a time for dose ${index + 1}." else "Each dose must have a different time.",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
