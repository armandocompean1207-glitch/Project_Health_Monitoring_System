package com.cse3310.projecthealthmonitoringsystem.communication

import android.Manifest
import com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as OutlinedCard
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import com.cse3310.projecthealthmonitoringsystem.monitoring.AlertHandoff
import com.cse3310.projecthealthmonitoringsystem.monitoring.alertDate
import kotlinx.coroutines.CancellationException
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsOutlinedButton as OutlinedButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextField as OutlinedTextField
import androidx.compose.material3.Text
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
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
fun CommunicationScreen(onBack: () -> Unit, alertId: Long? = null) {
    val context = LocalContext.current
    val app = context.phms()
    val userId = CurrentUser.userId
    val scope = rememberCoroutineScope()

    val designees by remember(userId) { app.communicationRepository.observeDesignees(userId) }.collectAsState(initial = emptyList())
    val log by remember(userId) { app.communicationRepository.observeCommunicationLog(userId) }.collectAsState(initial = emptyList())
    val alerts by remember(userId) { app.monitoringRepository.observeAlerts(userId) }.collectAsState(initial = emptyList())

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
    var method by remember { mutableStateOf(ContactMethod.EMAIL) }
    var formError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Designee?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectedAlertId by remember(alertId) { mutableStateOf(alertId) }
    var choosingAlert by remember { mutableStateOf(false) }
    var pendingContact by remember { mutableStateOf<Triple<Designee, ContactMethod, Long?>?>(null) }
    val dismissContactForm = rememberGuardedDismiss(showForm, busy) { showForm = false }
    val selectedAlert = alerts.firstOrNull { it.alertId == selectedAlertId }
    val listState = rememberLazyListState()


    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun openForm(d: Designee?) {
        editing = d
        name = d?.name ?: ""
        relationship = d?.relationship ?: ""
        phone = d?.phone ?: ""
        email = d?.email ?: ""
        method = d?.preferredMethod ?: ContactMethod.EMAIL
        scope.launch { listState.animateScrollToItem(0) }
        formError = null
        showForm = true
    }

    fun validate(): String? = HealthValidation.name(name, "Contact name")
        ?: HealthValidation.phone(phone) ?: HealthValidation.email(email)
        ?: when {
            method != ContactMethod.EMAIL && phone.isBlank() -> "Enter a phone number for texts and calls."
            method == ContactMethod.EMAIL && email.isBlank() -> "Enter an email address."
            else -> null
        }

    fun contact(d: Designee, how: ContactMethod) {
        pendingContact = Triple(d, how, selectedAlertId)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PhmsScreenHeader("Communication", "Keep your trusted people within reach.", PhmsIcons.Communication,
                if (showForm) dismissContactForm else onBack)
        }

        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Share an update", style = MaterialTheme.typography.titleMedium)
                    Text(selectedAlert?.message ?: if (selectedAlertId != null) "Selected alert is unavailable." else "A general check-in. No health record is included.")
                    TextButton(onClick = { choosingAlert = true }) { Text("Choose alert or general check-in") }
                    Text("You review and confirm before opening a message or call. Delivery is not confirmed.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (!smsAllowed) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Optional: allow automatic SMS alerts for contacts whose preferred method is SMS. Manual texts use your messaging app.")
                        Button(onClick = { smsPermission.launch(Manifest.permission.SEND_SMS) }) { Text("Allow SMS") }
                    }
                }
            }
        }

        if (!showForm) {
            item { Button(onClick = { openForm(null) }) { Text("Add contact") } }
        } else {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (editing == null) "Add contact" else "Edit contact", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(name, { name = it }, label = { Text("Name") }, enabled = !busy, isError = formError != null && HealthValidation.name(name) != null,
                            supportingText = { if (formError != null) HealthValidation.name(name)?.let { Text(it) } }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(relationship, { relationship = it }, label = { Text("Relationship (son, doctor, pharmacist…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(
                            phone, { phone = it }, label = { Text("Phone") }, singleLine = true, enabled = !busy,
                            isError = formError != null && (HealthValidation.phone(phone) != null || method != ContactMethod.EMAIL && phone.isBlank()),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            email, { email = it }, label = { Text("Email") }, singleLine = true, enabled = !busy,
                            isError = formError != null && (HealthValidation.email(email) != null || method == ContactMethod.EMAIL && email.isBlank()),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Preferred way to reach them")
                        Text("Choosing SMS enables automatic alerts to this contact when SMS permission is granted.", style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ContactMethod.values().forEach { m ->
                                FilterChip(selected = method == m, onClick = { method = m }, label = { Text(methodLabel(m)) })
                            }
                        }
                        formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = !busy, onClick = {
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
                                busy = true
                                scope.launch {
                                    try {
                                        if (current == null) app.communicationRepository.addDesignee(userId, d)
                                        else check(app.communicationRepository.editDesignee(userId, d)) { "Contact is no longer available." }
                                        toast("Contact saved")
                                        showForm = false
                                    } catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { formError = e.message ?: "Unable to save this contact." }
                                    finally { busy = false }
                                }
                            }) { Text("Save") }
                            OutlinedButton(enabled = !busy, onClick = dismissContactForm) { Text("Cancel") }
                        }
                    }
                }
            }
        }

        item { Text("Trusted contacts", style = MaterialTheme.typography.titleLarge) }
        if (designees.isEmpty()) {
            item { PhmsEmptyStateCard("No trusted contacts yet", "Add someone you trust, then choose how to reach them.", PhmsIcons.Communication) }
        }
        items(designees, key = { "designee-${it.designeeId}" }) { d ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PhmsIconBadge(PhmsIcons.Profile)
                    Text("${d.name} (${d.relationship.ifBlank { "Trusted contact" }})", style = MaterialTheme.typography.titleMedium)
                    if (d.phone.isNotBlank()) Text(d.phone)
                    if (d.email.isNotBlank()) Text(d.email)
                    Text("Prefers: ${methodLabel(d.preferredMethod)}", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (d.phone.isNotBlank()) {
                            TextButton(onClick = { contact(d, ContactMethod.SMS) }) { Icon(Icons.Outlined.Sms, null); Text(" Text") }
                            TextButton(onClick = { contact(d, ContactMethod.CALL) }) { Icon(Icons.Outlined.Call, null); Text(" Call") }
                        }
                        if (d.email.isNotBlank()) {
                            TextButton(onClick = { contact(d, ContactMethod.EMAIL) }) { Icon(Icons.Outlined.Email, null); Text(" Email") }
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { openForm(d) }) { Text("Edit") }
                        TextButton(onClick = { deleting = d }) { Text("Delete") }
                    }
                }
            }
        }

        item { Text("Communication log", style = MaterialTheme.typography.titleLarge) }
        if (log.isEmpty()) {
            item { Text("No contact activity yet.") }
        }
        items(log, key = { "log-${it.commId}" }) { entry ->
            val who = designees.firstOrNull { it.designeeId == entry.designeeId }?.name ?: "Removed contact"
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${methodLabel(entry.method)} to $who: ${statusLabel(entry.status)}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            alertDate(entry.sentAt) + if (entry.alertId != null) " · alert" else " · manual",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    TextButton(onClick = { scope.launch { app.communicationRepository.deleteLog(userId, entry.commId) } }) { Text("Delete") }
                }
            }
        }
        item { Text("") }
    }

    if (choosingAlert) {
        AlertDialog(onDismissRequest = { choosingAlert = false }, title = { Text("Choose an update") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { selectedAlertId = null; choosingAlert = false }) { Text("General check-in") }
                    alerts.forEach { alert ->
                        OutlinedButton(onClick = { selectedAlertId = alert.alertId; choosingAlert = false }) {
                            Text("${AlertHandoff.title(alert.type)} · ${alertDate(alert.createdAt)}\n${alert.message}")
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { choosingAlert = false }) { Text("Close") } })
    }

    pendingContact?.let { request ->
        val (person, how, linkedAlertId) = request
        val linked = alerts.firstOrNull { it.alertId == linkedAlertId }
        val destination = if (how == ContactMethod.EMAIL) person.email else person.phone
        AlertDialog(onDismissRequest = { if (!busy) pendingContact = null },
            title = { Text(when (how) { ContactMethod.SMS -> "Open SMS draft?"; ContactMethod.EMAIL -> "Open email draft?"; ContactMethod.CALL -> "Open dialer?" }) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium)
                    Text(destination)
                    Text(linked?.message ?: "General PHMS check-in")
                    Text(if (how == ContactMethod.CALL) "The dialer opens with this number. You decide whether to place the call."
                        else "A draft opens in another app. Review it before sending.")
                }
            },
            confirmButton = { TextButton(enabled = !busy && (linkedAlertId == null || linked != null), onClick = {
                busy = true
                scope.launch {
                    try {
                        val current = checkNotNull(app.communicationRepository.getDesignee(userId, person.designeeId)) { "Contact was removed." }
                        val alert = linkedAlertId?.let { checkNotNull(app.monitoringRepository.getAlert(userId, it)) { "Alert was removed." } }
                        val body = alert?.message ?: "Hi, this is a check-in from PHMS."
                        val intent = when (how) {
                            ContactMethod.SMS -> AlertSender.smsIntent(current.phone, body)
                            ContactMethod.CALL -> AlertSender.dialIntent(current.phone)
                            ContactMethod.EMAIL -> AlertSender.emailIntent(current.email, "PHMS update", body)
                        }
                        var opened = false
                        check(com.cse3310.projecthealthmonitoringsystem.auth.AccountSession.whileActive(context, userId) {
                            opened = openApp(context, intent)
                        }) { "Please sign in again." }
                        app.communicationRepository.logCommunication(userId, linkedAlertId, current.designeeId, how,
                            if (opened) DeliveryStatus.OPENED else DeliveryStatus.FAILED,
                            "User confirmed opening external app; delivery/call completion is not known")
                        pendingContact = null
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { formError = e.message; toast(e.message ?: "Unable to open communication action."); pendingContact = null }
                    finally { busy = false }
                }
            }) { Text(if (busy) "Opening…" else "Continue") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingContact = null }) { Text("Cancel") } })
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
                        toast("Contact deleted")
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
    DeliveryStatus.SENT -> "submitted to SMS service (delivery unconfirmed)"
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
