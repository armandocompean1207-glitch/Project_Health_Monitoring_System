package com.cse3310.projecthealthmonitoringsystem.monitoring

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton

/** History summary only; the existing details dialog retains full guidance and record information. */
@Composable
internal fun AlertHistoryCard(content: PresentedAlert, acknowledgedAt: Long?, onView: () -> Unit) {
    PhmsCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE8E1D8))) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.WarningAmber, "Health alert", tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp).size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(content.title, style = MaterialTheme.typography.titleSmall, color = Color(0xFF111827))
                content.recorded?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF111827)) }
                Text(acknowledgedAt?.let { "Acknowledged · ${alertDate(it)}" } ?: "Not acknowledged",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF111827))
            }
            PhmsTextButton(onClick = onView, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text("View", maxLines = 1, softWrap = false)
            }
        }
    }
}
