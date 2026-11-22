package com.cse3310.projecthealthmonitoringsystem.monitoring

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as OutlinedCard
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert
import com.cse3310.projecthealthmonitoringsystem.data.entity.AlertType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun alertDate(time: Long): String = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(time))

/** Alert records do not store clinical severity. Describe their type without inventing a risk score. */
fun alertIndicator(type: AlertType): String = when (type) {
    AlertType.MISSED_DOSE -> "Missed dose · Review needed"
    AlertType.CONFLICT -> "Interaction warning · Sample rule"
    AlertType.ABNORMAL_VITAL -> "Reading outside the saved reference range"
}

@Composable
fun AlertSummary(alert: Alert, onDetails: () -> Unit, onAcknowledge: (() -> Unit)? = null) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.WarningAmber, null, tint = com.cse3310.projecthealthmonitoringsystem.ui.theme.PhmsWarning, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(AlertHandoff.title(alert.type), style = MaterialTheme.typography.titleMedium)
                    Text(alertIndicator(alert.type), style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(alert.message, style = MaterialTheme.typography.bodyLarge)
            Text(alertDate(alert.createdAt), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (alert.acknowledgedAt == null) "Active" else "Acknowledged · ${alertDate(alert.acknowledgedAt)}",
                style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDetails) { Text("View details") }
                if (onAcknowledge != null) TextButton(onClick = onAcknowledge) { Text("Acknowledge") }
            }
        }
    }
}
