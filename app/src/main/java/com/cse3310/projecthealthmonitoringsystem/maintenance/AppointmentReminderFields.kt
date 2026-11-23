package com.cse3310.projecthealthmonitoringsystem.maintenance

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AppointmentReminderFields(mask: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Text("Remind me", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Switch(checked = mask != 0, enabled = enabled, onCheckedChange = { onChange(if (it) 10 else 0) })
        Text(if (mask == 0) "Reminders off" else "Device notifications", Modifier.padding(start = 8.dp))
    }
    if (mask != 0) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppointmentReminderOption.entries.forEach { option ->
                FilterChip(selected = mask and option.bit != 0, enabled = enabled,
                    onClick = { onChange(mask xor option.bit) }, label = { Text(option.label) })
            }
        }
        Text("Past reminder times are skipped. Android notification permissions and battery settings may delay reminders.",
            style = MaterialTheme.typography.bodySmall)
    }
}
