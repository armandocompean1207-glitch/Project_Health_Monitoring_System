package com.cse3310.projecthealthmonitoringsystem.maintenance

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.util.Patterns
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsIcons
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
internal fun VisitTimeField(value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        pickVisitTime(context, parseVisitTime(value) ?: System.currentTimeMillis()) { onChange(visitInput(it)) }
    }, modifier = Modifier.fillMaxWidth()) {
        Icon(PhmsIcons.Appointments, null)
        Text(parseVisitTime(value)?.let(::visitDisplay) ?: "Choose date and time", Modifier.padding(start = 8.dp))
    }
}

internal fun visitInput(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(time))

internal fun visitDisplay(time: Long): String =
    SimpleDateFormat("EEE, MMM d, yyyy · h:mm a", Locale.US).format(Date(time))

internal fun parseVisitTime(text: String): Long? {
    val value = text.trim()
    val position = ParsePosition(0)
    val parsed = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }.parse(value, position)
    return parsed?.time?.takeIf { position.index == value.length }
}

internal fun doctorContactError(phone: String, email: String): String? = when {
    phone.isNotBlank() && (phone.count { it.isDigit() } !in 7..15 ||
        phone.any { !it.isDigit() && it !in "+()- ." }) -> "Enter a valid phone number, or leave it blank."
    email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address, or leave it blank."
    else -> null
}

internal fun rangeError(min: Double?, max: Double?, secondMin: String, secondMax: String): String? {
    if (min == null || max == null || !min.isFinite() || !max.isFinite()) return "Enter finite numbers for the normal range."
    if (min > max) return "The minimum must not exceed the maximum."
    if (secondMin.isBlank() && secondMax.isBlank()) return null
    val low = secondMin.toDoubleOrNull()
    val high = secondMax.toDoubleOrNull()
    if (low == null || high == null || !low.isFinite() || !high.isFinite()) return "Enter both finite numbers for the second range."
    return if (low > high) "The second minimum must not exceed the maximum." else null
}

internal fun isAnnualCheckup(reason: String) = reason.trim().startsWith("Annual checkup", ignoreCase = true)

/** Native pickers use the device's local zone and 12/24-hour preference; no server booking is implied. */
internal fun pickVisitTime(context: Context, initial: Long, onSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(context, { _, year, month, day ->
        calendar.set(year, month, day)
        TimePickerDialog(context, { _, hour, minute ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            onSelected(calendar.timeInMillis)
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE),
            false).show()
    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
}
