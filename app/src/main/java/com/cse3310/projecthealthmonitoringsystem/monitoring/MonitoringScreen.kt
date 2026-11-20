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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun MonitoringScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { MonitoringStore.init(context); AlertSender.ensureChannel(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    var medName by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf(defaultTimeText()) }
    var graceText by remember { mutableStateOf("30") }
    var formError by remember { mutableStateOf<String?>(null) }

    var pendingConflicts by remember { mutableStateOf<List<ConflictWarning>>(emptyList()) }
    var pendingMed by remember { mutableStateOf("") }
    var pendingTime by remember { mutableStateOf(0L) }
    var pendingGrace by remember { mutableStateOf(30) }

    var dName by remember { mutableStateOf("") }
    var dRelationship by remember { mutableStateOf("") }
    var dPhone by remember { mutableStateOf("") }
    var dEmail by remember { mutableStateOf("") }

    fun schedule(med: String, at: Long, grace: Int) {
        MonitoringRepository.scheduleDose(context, med, at, grace)
        medName = ""
        timeText = defaultTimeText()
        Toast.makeText(context, "Dose scheduled for ${MonitoringRepository.formatTime(at)}", Toast.LENGTH_SHORT).show()
    }

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
            val needsSetup = !AlertSender.canNotify(context) || !AlertSender.canSendSms(context)
            if (needsSetup) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Turn on alerts", style = MaterialTheme.typography.titleMedium)
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
        }

        item {
            SectionCard("Schedule a dose to monitor") {
                OutlinedTextField(
                    value = medName, onValueChange = { medName = it },
                    label = { Text("Medication name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = timeText, onValueChange = { timeText = it },
                        label = { Text("Time (HH:mm)") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = graceText, onValueChange = { graceText = it.filter(Char::isDigit) },
                        label = { Text("Grace (min)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    val at = parseTodayTime(timeText)
                    val grace = graceText.toIntOrNull()
                    formError = when {
                        medName.isBlank() -> "Enter a medication name."
                        at == null -> "Enter the time as HH:mm, for example 08:30 or 21:00."
                        grace == null -> "Enter the grace period in minutes."
                        else -> null
                    }
                    if (formError == null) {
                        val conflicts = MonitoringRepository.checkConflicts(medName)
                        if (conflicts.isEmpty()) {
                            schedule(medName, at!!, grace!!)
                        } else {
                            pendingMed = medName
                            pendingTime = at!!
                            pendingGrace = grace!!
                            pendingConflicts = conflicts
                        }
                    }
                }) { Text("Schedule and check conflicts") }
            }
        }

        item { Text("Doses", style = MaterialTheme.typography.titleLarge) }
        if (MonitoringStore.doses.isEmpty()) {
            item { Text("No doses scheduled yet.") }
        }
        items(MonitoringStore.doses.sortedByDescending { it.scheduledAtMillis }, key = { it.id }) { dose ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dose.medicationName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        AssistChip(onClick = {}, label = { Text(dose.status.label()) })
                    }
                    Text("Due ${MonitoringRepository.formatTime(dose.scheduledAtMillis)} · grace ${dose.graceMinutes} min")
                    dose.takenAtMillis?.let { Text("Taken ${MonitoringRepository.formatTime(it)}") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dose.status == DoseStatus.SCHEDULED) {
                            Button(onClick = { MonitoringRepository.markTaken(context, dose.id) }) { Text("Mark taken") }
                            OutlinedButton(onClick = { MonitoringRepository.onDoseDeadline(context, dose.id) }) {
                                Text("Simulate missed")
                            }
                        }
                        TextButton(onClick = { MonitoringRepository.deleteDose(context, dose.id) }) { Text("Delete") }
                    }
                }
            }
        }

        item { Text("Designees", style = MaterialTheme.typography.titleLarge) }
        items(MonitoringStore.designees.toList(), key = { it.id }) { d ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${d.name} (${d.relationship.ifBlank { "designee" }})", style = MaterialTheme.typography.titleMedium)
                    if (d.phone.isNotBlank()) Text(d.phone)
                    if (d.email.isNotBlank()) Text(d.email)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (d.phone.isNotBlank()) {
                            TextButton(onClick = {
                                launch(context, AlertSender.smsIntent(d.phone, "Checking in from PHMS."))
                            }) { Text("Text") }
                            TextButton(onClick = { launch(context, AlertSender.dialIntent(d.phone)) }) { Text("Call") }
                        }
                        if (d.email.isNotBlank()) {
                            TextButton(onClick = {
                                launch(context, AlertSender.emailIntent(d.email, "PHMS update", latestAlertText()))
                            }) { Text("Email") }
                        }
                        TextButton(onClick = { MonitoringRepository.removeDesignee(d.id) }) { Text("Remove") }
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
                        MonitoringRepository.addDesignee(dName, dRelationship, dPhone, dEmail)
                        dName = ""; dRelationship = ""; dPhone = ""; dEmail = ""
                    }
                ) { Text("Add designee") }
            }
        }

        item { Text("Alert history", style = MaterialTheme.typography.titleLarge) }
        if (MonitoringStore.alerts.isEmpty()) {
            item { Text("No alerts yet.") }
        }
        items(MonitoringStore.alerts.toList(), key = { it.id }) { alert ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (alert.type == AlertType.MISSED_DOSE) "Missed dose" else "Medication conflict",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(MonitoringRepository.formatTime(alert.createdAtMillis), style = MaterialTheme.typography.bodySmall)
                    Text(alert.message)
                    Text(alert.sentTo, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Text("") }
    }

    if (pendingConflicts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingConflicts = emptyList() },
            title = { Text("Possible medication conflict") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pendingConflicts.forEach { Text("• ${it.drugA} + ${it.drugB}: ${it.reason}") }
                    Text(
                        "This check uses a small sample table for the class project and is not medical advice.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    MonitoringRepository.reportConflicts(context, pendingMed, pendingConflicts)
                    schedule(pendingMed, pendingTime, pendingGrace)
                    pendingConflicts = emptyList()
                }) { Text("Alert designees and schedule") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConflicts = emptyList() }) { Text("Cancel") }
            }
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

private fun DoseStatus.label() = when (this) {
    DoseStatus.SCHEDULED -> "Scheduled"
    DoseStatus.TAKEN -> "Taken"
    DoseStatus.MISSED -> "Missed"
}

private fun latestAlertText(): String =
    MonitoringStore.alerts.firstOrNull()?.message ?: "No alerts at this time."

private fun launch(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this device can do that.", Toast.LENGTH_SHORT).show()
    }
}

private fun defaultTimeText(): String {
    val c = Calendar.getInstance().apply { add(Calendar.MINUTE, 2) }
    return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

private fun parseTodayTime(text: String): Long? {
    val parts = text.trim().split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
