package com.cse3310.projecthealthmonitoringsystem.medication

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes
import java.util.Calendar

internal val MedicationYellow = Color(0xFFFFDF48)
internal val MedicationInk = Color(0xFF111827)
internal val MedicationBorder = Color(0xFFE8E1D8)

/** Scoped to Medication: preserves the global fonts, security UI and other feature screens. */
@Composable
internal fun MedicationDesign(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme.copy(
        primary = MedicationYellow, onPrimary = MedicationInk,
        primaryContainer = MedicationYellow, onPrimaryContainer = MedicationInk,
        secondaryContainer = Color(0xFFFEF9C3), onSecondaryContainer = MedicationInk,
        background = Color(0xFFFCFAF8), onBackground = MedicationInk,
        surface = Color.White, onSurface = MedicationInk, onSurfaceVariant = MedicationInk,
        outline = MedicationBorder, outlineVariant = MedicationBorder)
    MaterialTheme(colorScheme = colors, typography = MaterialTheme.typography.copy(
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 27.sp))) {
        Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = MedicationInk, content = content)
    }
}

@Composable
internal fun MedicationReminderCard(day: MedicationDayProgress) {
    val animated by animateFloatAsState(
        targetValue = if (day.total <= 0) 0f else (day.taken.toFloat() / day.total).coerceIn(0f, 1f),
        animationSpec = tween(450), label = "Today's completed doses")
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.White, contentColor = MedicationInk),
        border = BorderStroke(1.dp, MedicationBorder)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Reminders", style = MaterialTheme.typography.titleLarge)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                // Zero scheduled doses keep a genuinely empty ring, including after a day changes.
                val progress = if (day.total <= 0) 0f else animated
                if (maxWidth < 300.dp || fontScale > 1.3f) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        DoseCompletionCircle(day, progress, Modifier.align(Alignment.CenterHorizontally))
                        DoseCompletionDetails(day)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        DoseCompletionCircle(day, progress)
                        DoseCompletionDetails(day, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DoseCompletionCircle(day: MedicationDayProgress, progress: Float, modifier: Modifier = Modifier) {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 2f)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(if (entered) 1f else 0f, tween(250), label = "Progress circle entrance")
    Box(modifier.size(104.dp * scale).graphicsLayer {
        alpha = entrance
        scaleX = .97f + .03f * entrance
        scaleY = .97f + .03f * entrance
    }.semantics {
        contentDescription = "${day.taken} of ${day.total} doses taken today"
    }, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxSize(),
            color = MedicationYellow, trackColor = MedicationBorder, strokeWidth = 8.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${day.taken}/${day.total}", style = MaterialTheme.typography.titleLarge)
            Text("doses taken", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DoseCompletionDetails(day: MedicationDayProgress, modifier: Modifier = Modifier) {
    if (day.total == 0) {
        Text("No reminders right now.", modifier = modifier, style = MaterialTheme.typography.bodyLarge)
    } else Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${day.taken} ${if (day.taken == 1) "dose" else "doses"} taken", style = MaterialTheme.typography.titleMedium)
        Text(buildAnnotatedString {
            append("${day.remaining} remaining")
            if (day.missed > 0) withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                append(" · ${day.missed} missed")
            }
        }, style = MaterialTheme.typography.bodyLarge)
        if (day.unrecorded > 0) Text("${day.unrecorded} without a final intake record", style = MaterialTheme.typography.bodyMedium)
        day.nextAt?.let { at ->
            val cal = Calendar.getInstance().apply { timeInMillis = at }
            val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            Text("Next dose · ${DoseTimes.displayClock(minutes)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
