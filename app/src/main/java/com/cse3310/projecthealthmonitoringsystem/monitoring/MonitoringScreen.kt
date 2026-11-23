package com.cse3310.projecthealthmonitoringsystem.monitoring

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card
import androidx.compose.material3.FilterChip
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as OutlinedCard
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import com.cse3310.projecthealthmonitoringsystem.data.dao.MedicationConflict
import kotlinx.coroutines.CancellationException
import androidx.compose.material3.MaterialTheme
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert
import com.cse3310.projecthealthmonitoringsystem.data.entity.CommunicationLog
import com.cse3310.projecthealthmonitoringsystem.data.repository.MonitoringRepository
import kotlinx.coroutines.launch

@Composable
fun MonitoringScreen(onBack: () -> Unit, onOpenCommunication: () -> Unit = {}, onAlertCommunication: (Long) -> Unit = { onOpenCommunication() }) {
    val context = LocalContext.current
    val app = context.phms()
    val userId = CurrentUser.userId
    val scope = rememberCoroutineScope()
    LaunchedEffect(context) { AlertSender.ensureChannel(context) }

    val alerts by remember(userId) { app.monitoringRepository.observeAlerts(userId) }.collectAsState(initial = emptyList())
    val designees by remember(userId) { app.communicationRepository.observeDesignees(userId) }.collectAsState(initial = emptyList())
    val medications by remember(userId) { app.medicationRepository.observeActiveMedications(userId) }.collectAsState(initial = emptyList())

    val preferences = com.cse3310.projecthealthmonitoringsystem.settings.rememberMeasurementPreferences()
    val readings by remember(userId) { app.vitalsRepository.observeVitalSigns(userId) }.collectAsState(initial = emptyList())
    val readingsById = readings.associateBy { it.vital.vitalId }
    var needsContact by remember(userId) { mutableStateOf<Long?>(null) }
    fun notifyPerson(alertId: Long) {
        if (designees.isEmpty()) needsContact = alertId else onAlertCommunication(alertId)
    }

    var viewing by remember { mutableStateOf<Alert?>(null) }
    var viewingLog by remember { mutableStateOf<List<CommunicationLog>>(emptyList()) }

    var historyTab by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var conflicts by remember { mutableStateOf<List<MedicationConflict>>(emptyList()) }
    var simulate by remember { mutableStateOf<com.cse3310.projecthealthmonitoringsystem.data.entity.Medication?>(null) }
    LaunchedEffect(userId, medications) {
        try { conflicts = app.medicationRepository.detectConflicts(userId) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "Unable to load interaction rules." }
    }
    fun acknowledge(alert: Alert) {
        scope.launch {
            try { app.monitoringRepository.acknowledgeAlert(userId, alert.alertId) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "Unable to acknowledge this alert." }
        }
    }


    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
        onSurface = androidx.compose.ui.graphics.Color(0xFF0F172A),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF0F172A),
        primaryContainer = androidx.compose.ui.graphics.Color(0xFF065F46),
        onPrimaryContainer = androidx.compose.ui.graphics.Color.White)) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PhmsScreenHeader("Monitoring", "Your alerts and trusted contacts.", PhmsIcons.Alerts, onBack)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !historyTab, onClick = { historyTab = false },
                    label = { Text("Active (${alerts.count { it.acknowledgedAt == null }})") })
                FilterChip(selected = historyTab, onClick = { historyTab = true }, label = { Text("History") })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        val visible = alerts.filter { (it.acknowledgedAt != null) == historyTab }
        if (visible.isEmpty()) item {
            PhmsEmptyStateCard(if (historyTab) "No acknowledged alerts" else "No active alerts",
                if (historyTab) "Acknowledged alerts stay here for reference." else "New alerts from recorded doses and readings appear here.",
                PhmsIcons.Alerts, actionLabel = "Manage trusted contacts", onAction = onOpenCommunication, outlinedAction = true)
        }
        items(visible, key = { it.alertId }) { alert ->
            val content = presentAlert(alert, readingsById[alert.vitalId], preferences)
            val showDetails: () -> Unit = {
                scope.launch {
                    try {
                        viewingLog = app.communicationRepository.getLogsForAlert(userId, alert.alertId)
                        viewing = alert
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "Unable to load alert details." }
                }
            }
            if (historyTab) {
                AlertHistoryCard(content, alert.acknowledgedAt, showDetails)
            } else {
                AlertSummary(content, onDetails = showDetails,
                    onAcknowledge = if (alert.acknowledgedAt == null) ({ acknowledge(alert) }) else null,
                    onNotify = { notifyPerson(alert.alertId) })
            }
        }
        item {
            PhmsSectionHeader("Medication interactions", supportingText = "Check for potential interactions between your recorded medications.")
            Text("This limited check cannot guarantee medication safety. Ask your provider or pharmacist.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (conflicts.isEmpty()) item { Text("No potential interactions found in the available checks.",
            style = MaterialTheme.typography.bodyMedium) }
        items(conflicts) { pair ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${pair.medicationNameA} + ${pair.medicationNameB}", style = MaterialTheme.typography.titleMedium)
                    Text("Rule severity: ${pair.severity}", style = MaterialTheme.typography.labelLarge,
                        color = if (pair.severity.equals("HIGH", true)) MaterialTheme.colorScheme.error else com.cse3310.projecthealthmonitoringsystem.ui.theme.PhmsWarning)
                    Text(pair.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { PhmsActionCard("Trusted contacts", "${designees.size} saved · Manage contacts or choose how to reach them.",
            PhmsIcons.Communication, onOpenCommunication) }
        if (com.cse3310.projecthealthmonitoringsystem.auth.AccountSession.isDebug(context) &&
            userId == com.cse3310.projecthealthmonitoringsystem.data.SeedData.DEBUG_USER_ID) {
            item {
                Text("Debug demonstration only", style = MaterialTheme.typography.titleMedium)
                Text("Creates an irreversible fictional missed-dose record in the debug account only.")
            }
            items(medications) { med ->
                OutlinedButton(onClick = { simulate = med }) { Text("Simulate missed dose: ${med.name}") }
            }
        }
        item { Spacer(Modifier.padding(bottom = 16.dp)) }
    }

    }

    simulate?.let { med ->
        AlertDialog(onDismissRequest = { simulate = null }, title = { Text("Create fictional missed dose?") },
            text = { Text("Debug account only. This creates a permanent dose/alert record. No contacts are messaged by this demonstration action.") },
            confirmButton = { TextButton(onClick = {
                simulate = null
                scope.launch {
                    try {
                        val at = System.currentTimeMillis() - MonitoringRepository.DEFAULT_MISSED_WINDOW_MS - 60_000
                        val id = app.medicationRepository.scheduleDose(userId, med.medicationId, at)
                        id?.let { app.monitoringRepository.checkMissedDose(userId, it) }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "Unable to create debug alert." }
                }
            }) { Text("Create debug alert") } },
            dismissButton = { TextButton(onClick = { simulate = null }) { Text("Cancel") } })
    }

    needsContact?.let { id ->
        NoDesignatedPersonDialog(onDismiss = { needsContact = null }, onAdd = {
            needsContact = null
            onAlertCommunication(id)
        })
    }

    viewing?.let { alert ->
        val content = presentAlert(alert, readingsById[alert.vitalId], preferences)
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { AlertHeading(content.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    content.recorded?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    Text(content.guidance, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { viewing = null; notifyPerson(alert.alertId) }) { Text("Notify designated person") }
                    if (viewingLog.isEmpty()) {
                        Text("No contact attempts are logged for this alert.", style = MaterialTheme.typography.bodySmall)
                    }
                    viewingLog.forEach { log ->
                        val name = designees.firstOrNull { it.designeeId == log.designeeId }?.name ?: "Trusted contact"
                        Text("${log.method} to $name: ${log.status}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (alert.acknowledgedAt == null) {
                    TextButton(onClick = {
                        acknowledge(alert)
                        viewing = null
                    }) { Text("Acknowledge") }
                }
            },
            dismissButton = { TextButton(onClick = { viewing = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun openApp(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this device can do that.", Toast.LENGTH_SHORT).show()
    }
}
