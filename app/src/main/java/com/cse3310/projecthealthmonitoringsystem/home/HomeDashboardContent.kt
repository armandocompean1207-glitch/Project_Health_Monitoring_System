package com.cse3310.projecthealthmonitoringsystem.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

internal fun homeGreeting(hour: Int) = when (hour) {
    in 0..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/** One scrolling Home dashboard, backed by the existing account-scoped summary. */
@Composable
fun HomeDashboardScreen(username: String, onUpdateProfileClick: () -> Unit, onMedicationClick: () -> Unit,
    onVitalsClick: () -> Unit, onAppointmentsClick: () -> Unit, onAlertsClick: () -> Unit,
    modifier: Modifier = Modifier, summary: HomeDashboardSummary = HomeDashboardSummary(),
    onDietClick: () -> Unit = {}, onWeightClick: () -> Unit = onDietClick,
    onNotesClick: () -> Unit = {}, onMoreClick: () -> Unit = {}, now: Long = System.currentTimeMillis(),
    onSearchClick: () -> Unit = {}, onAlertSettingsClick: () -> Unit = onAlertsClick,
    onMedicalFormsClick: () -> Unit = {},
    onAlertDetails: (Long) -> Unit = { onAlertsClick() },
    onAcknowledgeAlert: (Long) -> Unit = {},
    onNotifyAlert: (Long) -> Unit = { onAlertsClick() }) {
    val scheme = MaterialTheme.colorScheme.copy(primary = HomePalette.Yellow, onPrimary = HomePalette.White,
        primaryContainer = HomePalette.Yellow, onPrimaryContainer = HomePalette.White,
        background = HomePalette.Background, onBackground = HomePalette.Ink,
        surface = HomePalette.White, onSurface = HomePalette.Ink, onSurfaceVariant = HomePalette.Ink)
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier.fillMaxSize(), color = HomePalette.Background, contentColor = HomePalette.Ink) {
            BoxWithConstraints {
                val twoColumns = maxWidth.value / LocalDensity.current.fontScale >= 320
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = HomePalette.Yellow, contentColor = HomePalette.White, shape = CircleShape, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(androidx.compose.ui.res.painterResource(com.cse3310.projecthealthmonitoringsystem.R.drawable.ic_phms_brandshield),
                                    null, Modifier.size(24.dp), tint = HomePalette.White)
                            }
                        }
                        Text("PHMS", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(start = 12.dp).weight(1f))
                        IconButton(onClick = onAlertsClick) { Icon(PhmsIcons.Alerts, "Notifications", Modifier.size(24.dp)) }
                        IconButton(onClick = onMoreClick) { Icon(PhmsIcons.DataMaintenance, "Settings", Modifier.size(24.dp)) }
                        Surface(onClick = onUpdateProfileClick, shape = CircleShape, color = HomePalette.Yellow,
                            modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PersonOutline, "Your profile", Modifier.size(24.dp)) }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val firstName = username.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
                        Text(homeGreeting(hour) + firstName.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty(),
                            style = MaterialTheme.typography.headlineLarge)
                        Text(SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US).format(Date(now)),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    }
                    Spacer(Modifier.height(4.dp))
                    HomeCard() {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                HomeIcon(PhmsIcons.Alerts)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    HomeCardTitle("Alerts")
                                    Text(if (summary.activeAlerts > 0) "${summary.activeAlerts} active ${if (summary.activeAlerts == 1) "alert" else "alerts"}"
                                        else "No active alerts.",
                                        style = MaterialTheme.typography.bodyLarge)
                                }
                                OutlinedButton(
                                    onClick = onAlertsClick,
                                    modifier = Modifier.widthIn(max = 144.dp).heightIn(min = 48.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = HomePalette.White,
                                        contentColor = HomePalette.Ink),
                                    border = BorderStroke(1.dp, HomePalette.Border),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text("View alerts", maxLines = 2,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                            summary.alert?.let { alert ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    com.cse3310.projecthealthmonitoringsystem.monitoring.AlertHeading(alert.title)
                                    Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                                    alert.recorded?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                    if (alert.alertId > 0) com.cse3310.projecthealthmonitoringsystem.monitoring.AlertActions(
                                        onDetails = { onAlertDetails(alert.alertId) },
                                        onAcknowledge = { onAcknowledgeAlert(alert.alertId) },
                                        onNotify = { onNotifyAlert(alert.alertId) })
                                }
                            }
                        }
                    }
                    HomeCard() {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                HomeIcon(PhmsIcons.Appointments)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    HomeCardTitle("Appointments")
                                    Text(summary.nextAppointment?.let { "With ${it.providerName}" } ?: "No upcoming appointments.", style = MaterialTheme.typography.bodyLarge)
                                    summary.nextAppointment?.let { Text(it.dateAndTime, style = MaterialTheme.typography.bodyMedium) }
                                }
                            }
                            HomeCareAction(if (summary.nextAppointment == null) "Add appointment" else "View appointment", onAppointmentsClick)
                        }
                    }
                    HomeCard() {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            val med = summary.medication
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                HomeIcon(PhmsIcons.Medication)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    HomeCardTitle("Today's Medicine")
                                    Text(med?.nextMedicationName ?: if (med == null) "Add your first medication." else "No more doses due.",
                                        style = MaterialTheme.typography.bodyLarge)
                                    med?.nextDoseTime?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                    med?.instructions?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                    if (med != null && med.totalDoses > 0) {
                                        LinearProgressIndicator(progress = { (med.takenDoses.toFloat() / med.totalDoses).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth(), trackColor = HomePalette.Border)
                                        Text("${med.takenDoses} of ${med.totalDoses} taken", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            HomeCareAction(if (med == null) "Add medicine" else "View schedule", onMedicationClick)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        HomeSectionTitle("Clinical vitals", Modifier.weight(1f))
                        HomeTextAction("View all", onVitalsClick)
                    }
                    val readings = listOf(
                        Triple("Blood pressure", summary.bloodPressure, PhmsIcons.BloodPressure),
                        Triple("Heart rate", summary.heartRate, Icons.Outlined.MonitorHeart),
                        Triple("Blood glucose", summary.glucose, PhmsIcons.Glucose),
                        Triple("Blood oxygen", summary.oxygen, Icons.Outlined.Air))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        readings.chunked(if (twoColumns) 2 else 1).forEach { row ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { (title, reading, icon) ->
                                    HomeVitalCard(title, reading, icon, onVitalsClick, Modifier.weight(1f).fillMaxHeight(), onAlert = onAlertsClick)
                                }
                            }
                        }
                    }
                    TextButton(onClick = onVitalsClick, contentPadding = PaddingValues(horizontal = 4.dp),
                        modifier = Modifier.heightIn(min = 48.dp)) { HomeSectionTitle("Add readings") }
                    HomeJournalCard("Weight history", "Your recorded weight over time", summary.latestWeight,
                        PhmsIcons.Weight, "Add weight", onWeightClick) {
                        summary.weightDate?.let { Text("Recorded $it", style = MaterialTheme.typography.bodyMedium) }
                        if (summary.weightHistory.size >= 2) WeightTrend(summary.weightHistory)
                    }
                    HomeJournalCard("Today's Meal", "Your daily meal choice",
                        if (summary.mealCount > 0) "${summary.calories} kcal" else null, PhmsIcons.Diet, "Add a meal", onDietClick) {
                        if (summary.mealCount > 0) Text("${summary.mealCount} ${if (summary.mealCount == 1) "meal" else "meals"} recorded", style = MaterialTheme.typography.bodyMedium)
                        if (summary.calorieGoal > 0) {
                            LinearProgressIndicator(progress = { (summary.calories.toFloat() / summary.calorieGoal).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(), trackColor = HomePalette.Border)
                            Text("of your ${summary.calorieGoal} kcal goal", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HomeSectionTitle("Quick actions")
                    val actions = listOf(Triple("Notes", PhmsIcons.Notes, onNotesClick),
                        Triple("Alert settings", PhmsIcons.Alerts, onAlertSettingsClick),
                        Triple("Search", PhmsIcons.Search, onSearchClick),
                        Triple("Medical forms", Icons.AutoMirrored.Outlined.Assignment, onMedicalFormsClick))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        actions.chunked(if (twoColumns) 2 else 1).forEach { row ->
                            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { (title, icon, action) ->
                                    HomeCard(Modifier.weight(1f).fillMaxHeight(), onClick = action) {
                                        Row(Modifier.heightIn(min = 88.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            HomeIcon(icon, 40)
                                            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightTrend(values: List<Float>) {
    val color = MaterialTheme.colorScheme.primary
    val edge = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val low = values.min()
        val spread = values.max() - low
        val path = Path()
        val positions = values.mapIndexed { i, value ->
            androidx.compose.ui.geometry.Offset(size.width * i / values.lastIndex,
                size.height * (.85f - (if (spread == 0f) .5f else (value - low) / spread) * .7f))
        }
        positions.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
        val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .45f), color.copy(alpha = 0f))))
        drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        positions.forEach { drawCircle(color, 3.dp.toPx(), it) }
        drawCircle(edge, 5.dp.toPx(), positions.last())
        drawCircle(color, 3.dp.toPx(), positions.last())
    }
}
