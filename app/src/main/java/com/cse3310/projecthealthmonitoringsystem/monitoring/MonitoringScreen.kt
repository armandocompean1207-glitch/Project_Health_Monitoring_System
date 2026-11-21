package com.cse3310.projecthealthmonitoringsystem.monitoring

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert
import com.cse3310.projecthealthmonitoringsystem.data.entity.CommunicationLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.ContactMethod
import com.cse3310.projecthealthmonitoringsystem.data.entity.Designee
import com.cse3310.projecthealthmonitoringsystem.data.repository.MonitoringRepository
import kotlinx.coroutines.launch

@Composable
fun MonitoringScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.phms()
    val userId = CurrentUser.userId
    val scope = rememberCoroutineScope()
    remember { AlertSender.ensureChannel(context) }

    val alerts by remember { app.monitoringRepository.observeAlerts(userId) }.collectAsState(initial = emptyList())
    val designees by remember { app.communicationRepository.observeDesignees(userId) }.collectAsState(initial = emptyList())
    val medications by remember { app.medicationRepository.observeActiveMedications(userId) }.collectAsState(initial = emptyList())

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    var viewing by remember { mutableStateOf<Alert?>(null) }
    var viewingLog by remember { mutableStateOf<List<CommunicationLog>>(emptyList()) }

    var dName by remember { mutableStateOf("") }
    var dRelationship by remember { mutableStateOf("") }
    var dPhone by remember { mutableStateOf("") }
    var dEmail by remember { mutableStateOf("") }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Back to More") }
            Text("Monitoring", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Missed doses and medication conflicts are sent to you and your designees.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {
            if (!AlertSender.canNotify(context) || !AlertSender.canSendSms(context)) {
                SectionCard("Turn on alerts") {
                    Text("Allow notifications and SMS so PHMS can alert you and your designees.")
                    Button(onClick = {
                        val perms = mutableListOf(Manifest.permission.SEND_SMS)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms += Manifest.permission.POST_NOTIFICATIONS
                        }
                        permissionLauncher.launch(perms.toTypedArray())
                    }) { Text("Allow") }
                }
            }
        }

        item { Text("Alerts", style = MaterialTheme.typography.titleLarge) }
        if (alerts.isEmpty()) {
            item { Text("No alerts yet.") }
        }
        items(alerts, key = { it.alertId }) { alert ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            AlertHandoff.title(alert.type),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        AssistChip(onClick = {}, label = { Text(if (alert.acknowledgedAt != null) "Acknowledged" else "New") })
                    }
                    Text(DoseTimes.formatTime(alert.createdAt), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            scope.launch {
                                viewingLog = app.communicationRepository.getLogsForAlert(userId, alert.alertId)
                                viewing = alert
                            }
                        }) { Text("View details") }
                        if (alert.acknowledgedAt == null) {
                            TextButton(onClick = {
                                scope.launch { app.monitoringRepository.acknowledgeAlert(userId, alert.alertId) }
                            }) { Text("Acknowledge") }
                        }
                    }
                }
            }
        }

        item { Text("Designees", style = MaterialTheme.typography.titleLarge) }
        items(designees, key = { it.designeeId }) { d ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${d.name} (${d.relationship.ifBlank { "designee" }})", style = MaterialTheme.typography.titleMedium)
                    if (d.phone.isNotBlank()) Text(d.phone)
                    if (d.email.isNotBlank()) Text(d.email)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (d.phone.isNotBlank()) {
                            TextButton(onClick = { openApp(context, AlertSender.smsIntent(d.phone, "Checking in from PHMS.")) }) { Text("Text") }
                            TextButton(onClick = { openApp(context, AlertSender.dialIntent(d.phone)) }) { Text("Call") }
                        }
                        if (d.email.isNotBlank()) {
                            TextButton(onClick = {
                                openApp(context, AlertSender.emailIntent(d.email, "PHMS update", alerts.firstOrNull()?.message ?: "No alerts at this time."))
                            }) { Text("Email") }
                        }
                        TextButton(onClick = {
                            scope.launch { app.communicationRepository.deleteDesignee(userId, d.designeeId) }
                        }) { Text("Remove") }
                    }
                }
            }
        }
        item {
            SectionCard("Add a designee") {
                OutlinedTextField(dName, { dName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dRelationship, { dRelationship = it }, label = { Text("Relationship (son, doctor, pharmacist…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    dPhone, { dPhone = it }, label = { Text("Phone") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    dEmail, { dEmail = it }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = dName.isNotBlank() && (dPhone.isNotBlank() || dEmail.isNotBlank()),
                    onClick = {
                        val designee = Designee(
                            userId = userId,
                            name = dName.trim(),
                            relationship = dRelationship.trim(),
                            phone = dPhone.trim(),
                            email = dEmail.trim(),
                            preferredMethod = if (dPhone.isNotBlank()) ContactMethod.SMS else ContactMethod.EMAIL
                        )
                        scope.launch {
                            app.communicationRepository.addDesignee(userId, designee)
                            dName = ""; dRelationship = ""; dPhone = ""; dEmail = ""
                        }
                    }
                ) { Text("Add designee") }
            }
        }

        if (medications.isNotEmpty()) {
            item {
                SectionCard("Demo: simulate a missed dose") {
                    Text("Creates a dose 31 minutes in the past so the missed-dose rule runs now.", style = MaterialTheme.typography.bodySmall)
                    medications.forEach { med ->
                        OutlinedButton(onClick = {
                            scope.launch {
                                val at = System.currentTimeMillis() - MonitoringRepository.DEFAULT_MISSED_WINDOW_MS - 60_000L
                                val doseId = app.medicationRepository.scheduleDose(userId, med.medicationId, at)
                                val alert = doseId?.let { app.monitoringRepository.checkMissedDose(userId, it) }
                                if (alert != null) toast(AlertHandoff.send(context, alert)) else toast("Could not create the dose")
                            }
                        }) { Text("Miss a dose of ${med.name}") }
                    }
                }
            }
        }
        item { Text("") }
    }

    viewing?.let { alert ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(AlertHandoff.title(alert.type)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(DoseTimes.formatTime(alert.createdAt), style = MaterialTheme.typography.bodySmall)
                    Text(alert.message)
                    if (viewingLog.isEmpty()) {
                        Text("No designees were contacted.", style = MaterialTheme.typography.bodySmall)
                    }
                    viewingLog.forEach { log ->
                        val name = designees.firstOrNull { it.designeeId == log.designeeId }?.name ?: "Designee"
                        Text("${log.method} to $name: ${log.status}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (alert.acknowledgedAt == null) {
                    TextButton(onClick = {
                        scope.launch { app.monitoringRepository.acknowledgeAlert(userId, alert.alertId) }
                        viewing = null
                    }) { Text("Acknowledge") }
                }
            },
            dismissButton = { TextButton(onClick = { viewing = null }) { Text("Exit") } }
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
