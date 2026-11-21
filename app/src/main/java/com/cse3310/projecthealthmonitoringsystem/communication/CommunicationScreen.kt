package com.cse3310.projecthealthmonitoringsystem.communication

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.entity.ContactMethod
import com.cse3310.projecthealthmonitoringsystem.data.entity.DeliveryStatus
import com.cse3310.projecthealthmonitoringsystem.data.entity.Designee
import com.cse3310.projecthealthmonitoringsystem.monitoring.AlertSender
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import kotlinx.coroutines.launch

@Composable
fun CommunicationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.phms()
    val userId = CurrentUser.userId
    val scope = rememberCoroutineScope()

    val designees by remember { app.communicationRepository.observeDesignees(userId) }.collectAsState(initial = emptyList())
    val log by remember { app.communicationRepository.observeCommunicationLog(userId) }.collectAsState(initial = emptyList())
    val alerts by remember { app.monitoringRepository.observeAlerts(userId) }.collectAsState(initial = emptyList())

    var smsAllowed by remember { mutableStateOf(AlertSender.canSendSms(context)) }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        smsAllowed = AlertSender.canSendSms(context)
    }

    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Designee?>(null) }
    var name by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(ContactMethod.SMS) }
    var formError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Designee?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun openForm(d: Designee?) {
        editing = d
        name = d?.name ?: ""
        relationship = d?.relationship ?: ""
        phone = d?.phone ?: ""
        email = d?.email ?: ""
        method = d?.preferredMethod ?: ContactMethod.SMS
        formError = null
        showForm = true
    }

    fun validate(): String? = when {
        name.isBlank() -> "Enter a name."
        phone.isBlank() && email.isBlank() -> "Enter a phone number or an email."
        phone.isNotBlank() && phone.count { it.isDigit() } < 7 -> "Enter a valid phone number."
        email.isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email."
        method != ContactMethod.EMAIL && phone.isBlank() -> "Text and call need a phone number."
        method == ContactMethod.EMAIL && email.isBlank() -> "Email needs an email address."
        else -> null
    }

    fun latestUpdate(): String =
        alerts.firstOrNull()?.message ?: "Hi, this is a check-in from my Personal Health Monitoring app."

    fun contact(d: Designee, how: ContactMethod) {
        val body = latestUpdate()
        val opened = when (how) {
            ContactMethod.SMS -> {
                if (AlertSender.canSendSms(context)) {
                    val sent = AlertSender.sendSms(context, d.phone, body)
                    scope.launch {
                        app.communicationRepository.logCommunication(
                            userId, null, d.designeeId, ContactMethod.SMS,
                            if (sent) DeliveryStatus.SENT else DeliveryStatus.FAILED, "Manual text"
                        )
                    }
                    toast(if (sent) "Text sent to ${d.name}" else "Text to ${d.name} failed")
                    return
                }
                openApp(context, AlertSender.smsIntent(d.phone, body))
            }
            ContactMethod.CALL -> openApp(context, AlertSender.dialIntent(d.phone))
            ContactMethod.EMAIL -> openApp(context, AlertSender.emailIntent(d.email, "PHMS update", body))
        }
        scope.launch {
            app.communicationRepository.logCommunication(
                userId, null, d.designeeId, how,
                if (opened) DeliveryStatus.OPENED else DeliveryStatus.FAILED, "Manual"
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Back to More") }
            Text("Communication", style = MaterialTheme.typography.headlineMedium)
            Text("People who get your alerts, and how to reach them.", style = MaterialTheme.typography.bodyMedium)
        }

        if (!smsAllowed) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Allow SMS so alerts can be texted to your designees automatically.")
                        Button(onClick = { smsPermission.launch(Manifest.permission.SEND_SMS) }) { Text("Allow SMS") }
                    }
                }
            }
        }

        if (!showForm) {
            item { Button(onClick = { openForm(null) }) { Text("Add designee") } }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (editing == null) "Add designee" else "Edit designee", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(relationship, { relationship = it }, label = { Text("Relationship (son, doctor, pharmacist…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            phone, { phone = it }, label = { Text("Phone") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            email, { email = it }, label = { Text("Email") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Preferred way to reach them")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ContactMethod.values().forEach { m ->
                                FilterChip(selected = method == m, onClick = { method = m }, label = { Text(methodLabel(m)) })
                            }
                        }
                        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                formError = validate()
                                if (formError != null) return@Button
                                val current = editing
                                val d = Designee(
                                    designeeId = current?.designeeId ?: 0,
                                    userId = userId,
                                    name = name.trim(),
                                    relationship = relationship.trim(),
                                    phone = phone.trim(),
                                    email = email.trim(),
                                    preferredMethod = method
                                )
                                scope.launch {
                                    if (current == null) app.communicationRepository.addDesignee(userId, d)
                                    else app.communicationRepository.editDesignee(userId, d)
                                    toast("Designee saved")
                                    showForm = false
                                }
                            }) { Text("Save") }
                            OutlinedButton(onClick = { showForm = false }) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        item { Text("Designees", style = MaterialTheme.typography.titleLarge) }
        if (designees.isEmpty()) {
            item { Text("No designees yet. Add someone to be told about missed doses and conflicts.") }
        }
        items(designees, key = { "designee-${it.designeeId}" }) { d ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${d.name} (${d.relationship.ifBlank { "designee" }})", style = MaterialTheme.typography.titleMedium)
                    if (d.phone.isNotBlank()) Text(d.phone)
                    if (d.email.isNotBlank()) Text(d.email)
                    Text("Prefers: ${methodLabel(d.preferredMethod)}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (d.phone.isNotBlank()) {
                            TextButton(onClick = { contact(d, ContactMethod.SMS) }) { Text("Text") }
                            TextButton(onClick = { contact(d, ContactMethod.CALL) }) { Text("Call") }
                        }
                        if (d.email.isNotBlank()) {
                            TextButton(onClick = { contact(d, ContactMethod.EMAIL) }) { Text("Email") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { openForm(d) }) { Text("Edit") }
                        TextButton(onClick = { deleting = d }) { Text("Delete") }
                    }
                }
            }
        }

        item { Text("Communication log", style = MaterialTheme.typography.titleLarge) }
        if (log.isEmpty()) {
            item { Text("Nothing sent yet.") }
        }
        items(log, key = { "log-${it.commId}" }) { entry ->
            val who = designees.firstOrNull { it.designeeId == entry.designeeId }?.name ?: "Removed designee"
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${methodLabel(entry.method)} to $who: ${statusLabel(entry.status)}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            DoseTimes.formatTime(entry.sentAt) + if (entry.alertId != null) " · alert" else " · manual",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { scope.launch { app.communicationRepository.deleteLog(userId, entry.commId) } }) { Text("Delete") }
                }
            }
        }
        item { Text("") }
    }

    deleting?.let { d ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${d.name}?") },
            text = { Text("They will stop getting your alerts. Past log entries are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        app.communicationRepository.deleteDesignee(userId, d.designeeId)
                        toast("Designee deleted")
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

private fun methodLabel(m: ContactMethod): String = when (m) {
    ContactMethod.SMS -> "Text"
    ContactMethod.EMAIL -> "Email"
    ContactMethod.CALL -> "Call"
}

private fun statusLabel(s: DeliveryStatus): String = when (s) {
    DeliveryStatus.SENT -> "sent"
    DeliveryStatus.FAILED -> "failed"
    DeliveryStatus.OPENED -> "opened"
}

private fun openApp(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this device can do that.", Toast.LENGTH_SHORT).show()
        false
    }
