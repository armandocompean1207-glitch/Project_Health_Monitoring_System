package com.cse3310.projecthealthmonitoringsystem.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Approved tokens are local to Home, leaving the other modules unchanged. */
internal object HomePalette {
    val Background = Color(0xFFFCFAF8)
    val Yellow = Color(0xFFFFDF48)
    val Ink = Color(0xFF111827)
    val Border = Color(0xFFE7E2DF)
    val White = Color.White
}

@Composable
internal fun HomeCard(modifier: Modifier = Modifier, yellowBorder: Boolean = false, onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit) {
    val colors = CardDefaults.outlinedCardColors(containerColor = Color.White,
        contentColor = HomePalette.Ink)
    val border = BorderStroke(1.dp, if (yellowBorder) HomePalette.Yellow else HomePalette.Border)
    if (onClick == null) OutlinedCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        colors = colors, border = border, content = content)
    else OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        colors = colors, border = border, content = content)
}

@Composable
internal fun HomeIcon(icon: ImageVector, size: Int = 48) {
    Surface(color = HomePalette.Yellow, contentColor = HomePalette.Ink, shape = CircleShape, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp)) }
    }
}

@Composable
internal fun HomeCardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 27.sp,
        fontWeight = FontWeight.Bold), modifier = modifier)
}

@Composable
internal fun HomeSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), modifier = modifier)
}

@Composable
internal fun HomeTextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = HomePalette.Ink)) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp))
    }
}

@Composable
internal fun HomeYellowAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = HomePalette.Yellow, contentColor = HomePalette.Ink),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Equal width, centering, and touch targets for the three care-section actions. */
@Composable
internal fun HomeCareAction(text: String, onClick: () -> Unit, emphasized: Boolean = true) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val actionModifier = Modifier.widthIn(max = 240.dp).fillMaxWidth()
        if (emphasized) HomeYellowAction(text, onClick, actionModifier)
        else HomeTextAction(text, onClick, actionModifier)
    }
}

@Composable
internal fun HomeVitalCard(title: String, reading: HomeVitalRecord?, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    HomeCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HomeIcon(icon)
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
            Text(reading?.displayValue ?: "—", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
            reading?.let {
                Text(it.unit, style = MaterialTheme.typography.bodyMedium)
                Text(it.recordedAt, style = MaterialTheme.typography.bodySmall)
                if (it.isAbnormal) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.WarningAmber, "Health alert", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Text("Review alert", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = HomePalette.Ink)) {
                Text("Add reading", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
internal fun HomeJournalCard(title: String, description: String, value: String?, icon: ImageVector,
    action: String, onClick: () -> Unit, details: @Composable ColumnScope.() -> Unit) {
    HomeCard(onClick = onClick) {
        Column(Modifier.fillMaxWidth().heightIn(min = 144.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                HomeIcon(icon)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HomeCardTitle(title)
                    Box(Modifier.heightIn(min = 24.dp)) { Text(description, style = MaterialTheme.typography.bodyLarge) }
                }
                Text(value ?: "—", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.widthIn(max = 112.dp))
            }
            details()
            HomeTextAction(action, onClick, Modifier.padding(start = 12.dp))
        }
    }
}
