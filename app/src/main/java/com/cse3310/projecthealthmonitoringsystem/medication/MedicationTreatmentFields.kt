package com.cse3310.projecthealthmonitoringsystem.medication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsDateInput
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
    onStart: (Long) -> Unit, onEnd: (Long) -> Unit, onOngoing: (Boolean) -> Unit, showErrors: Boolean,
    onValidityChange: (Boolean) -> Unit = {}) {
    var startValid by remember { mutableStateOf(true) }
    var endValid by remember { mutableStateOf(true) }
    SideEffect { onValidityChange(startValid && (ongoing || endValid)) }
    PhmsDateInput(start, { it?.let { value -> onStart(MedicationTimes.dayStart(value)) } },
        "Start date", enabled = enabled, onValidityChange = { startValid = it })
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = ongoing, onCheckedChange = onOngoing, enabled = enabled)
        Text("Ongoing medication", style = MaterialTheme.typography.bodyLarge)
    }
    PhmsDateInput(if (ongoing) null else end, { it?.let { value -> onEnd(MedicationTimes.dayEnd(value)) } },
        "End date", enabled = enabled && !ongoing, required = !ongoing, minDate = start,
        onValidityChange = { endValid = it })
    if (showErrors && !ongoing && (end == null || end < start)) {
        Text("Choose an end date on or after the start date.", color = MaterialTheme.colorScheme.error)
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
