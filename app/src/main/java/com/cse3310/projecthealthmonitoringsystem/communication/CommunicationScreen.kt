package com.cse3310.projecthealthmonitoringsystem.communication

import android.Manifest
import com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.produceState
import com.cse3310.projecthealthmonitoringsystem.settings.rememberMeasurementPreferences
import com.cse3310.projecthealthmonitoringsystem.settings.loadMeasurementPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    val alerts by remember(userId, now) { app.monitoringRepository.observeShareableAlerts(userId, now) }.collectAsState(initial = emptyList())
    val readings by remember(userId) { app.vitalsRepository.observeVitalSigns(userId) }.collectAsState(initial = emptyList())
    val types by remember { app.vitalsRepository.observeVitalTypes() }.collectAsState(initial = emptyList())
    val preferences = rememberMeasurementPreferences()
    val readingsById = readings.associateBy { it.vital.vitalId }
    val yellow = Color(0xFFFFDF48)
    val neutralBorder = Color(0xFFE8E1D8)

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
    var methods by remember { mutableStateOf(setOf(ContactMethod.EMAIL)) }
    var automaticSms by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Designee?>(null) }
    var busy by remember { mutableStateOf(false) }
    var selectedAlertId by remember(alertId) { mutableStateOf(alertId) }
    var choosingAlert by remember { mutableStateOf(false) }
    var updateChosen by remember(alertId) { mutableStateOf(alertId != null) }
    var choosingRecipient by remember { mutableStateOf(false) }
    var shareUpdateId by remember(userId) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var showingHistory by remember(userId) { mutableStateOf(false) }
    val history = remember(log) { communicationHistory(log) }
    var pendingContact by remember { mutableStateOf<Triple<Designee, ContactMethod, Long?>?>(null) }
    var featureUnavailable by remember(userId) { mutableStateOf(false) }
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
        methods = d?.selectedMethods() ?: setOf(ContactMethod.EMAIL)
        automaticSms = d?.allowsAutomaticSms() ?: false
        scope.launch { listState.animateScrollToItem(0) }
        formError = null
        showForm = true
    }

    fun validate(): String? = HealthValidation.name(name, "Contact name")
        ?: HealthValidation.phone(phone) ?: HealthValidation.email(email)
        ?: when {
            methods.isEmpty() -> "Select at least one contact method."
            methods.any { it == ContactMethod.SMS || it == ContactMethod.CALL } && phone.isBlank() -> "Enter a phone number for texts and calls."
            ContactMethod.EMAIL in methods && email.isBlank() -> "Enter an email address."
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
                    if (selectedAlertId != null && selectedAlert == null) Text("This alert is no longer active. Choose another alert.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShareChoice(selectedAlert?.let { alert ->
                            updateTitle(alert, readingsById[alert.vitalId], types.firstOrNull { type ->
                                type.vitalTypeId == readingsById[alert.vitalId]?.vital?.vitalTypeId })
                        } ?: "Choose alert", updateChosen && selectedAlertId != null,
                            Modifier.weight(1f), warning = selectedAlert != null, icon = PhmsIcons.Alerts) { choosingAlert = true }
                        ShareChoice("General check-in", updateChosen && selectedAlertId == null,
                            Modifier.weight(1f)) {
                            selectedAlertId = null; updateChosen = true
                            shareUpdateId = java.util.UUID.randomUUID().toString()
                        }
                    }
                    Text("General check-ins do not include health records.", style = MaterialTheme.typography.bodySmall)
                    if (updateChosen) Button(
                        onClick = { choosingRecipient = true },
                        enabled = selectedAlertId == null || selectedAlert != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = yellow, contentColor = Color.Black)
                    ) { Text("Share update") }
                }
            }
        }
        if (!smsAllowed) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Optional: allow SMS permission for contacts who have automatic text alerts enabled. Manual texts use your messaging app.")
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
                            isError = formError != null && (HealthValidation.phone(phone) != null || methods.any { it == ContactMethod.SMS || it == ContactMethod.CALL } && phone.isBlank()),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            email, { email = it }, label = { Text("Email") }, singleLine = true, enabled = !busy,
                            isError = formError != null && (HealthValidation.email(email) != null || ContactMethod.EMAIL in methods && email.isBlank()),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Preferred contact methods")
                        Text("Select all that apply.", style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ContactMethod.entries.forEach { m ->
                                FilterChip(selected = m in methods, enabled = !busy,
                                    onClick = {
                                        methods = if (m in methods) methods - m else methods + m
                                        if (ContactMethod.SMS !in methods) automaticSms = false
                                    },
                                    label = { Text(methodLabel(m)) },
                                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.White,
                                        labelColor = Color(0xFF111827), selectedContainerColor = yellow,
                                        selectedLabelColor = Color(0xFF111827)),
                                    border = BorderStroke(1.dp, if (m in methods) yellow else neutralBorder))
                            }
                        }
                        if (ContactMethod.SMS in methods) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = automaticSms, enabled = !busy, onCheckedChange = { automaticSms = it })
                                Text("Allow automatic SMS alerts", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Automatic texts require your consent and Android SMS permission. Carrier charges may apply.",
                                style = MaterialTheme.typography.bodySmall)
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
                                    preferredMethod = current?.preferredMethod?.takeIf { it in methods }
                                        ?: ContactMethod.entries.first { it in methods },
                                    contactMethodsMask = Designee.methodsMask(methods),
                                    automaticSmsConsent = automaticSms && ContactMethod.SMS in methods
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
            item {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primaryContainer = yellow, onPrimaryContainer = Color(0xFF111827))) {
                    PhmsEmptyStateCard("No trusted contacts yet", "Add someone you trust, then choose how to reach them.", PhmsIcons.Communication)
                }
            }
        }
        items(designees, key = { "designee-${it.designeeId}" }) { d ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primaryContainer = yellow, onPrimaryContainer = Color.Black)) {
                        PhmsIconBadge(PhmsIcons.Profile)
                    }
                    Text("${d.name} (${d.relationship.ifBlank { "Trusted contact" }})", style = MaterialTheme.typography.titleMedium)
                    if (d.phone.isNotBlank()) Text(d.phone)
                    if (d.email.isNotBlank()) Text(d.email)
                    Text("Prefers: ${d.selectedMethods().joinToString { methodLabel(it) }}", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (d.phone.isNotBlank() && ContactMethod.SMS in d.selectedMethods())
                            TextButton(enabled = selectedAlertId == null || selectedAlert != null, onClick = { contact(d, ContactMethod.SMS) }) { Icon(Icons.Outlined.Sms, null); Text(" Text") }
                        if (d.phone.isNotBlank() && ContactMethod.CALL in d.selectedMethods())
                            TextButton(enabled = selectedAlertId == null || selectedAlert != null, onClick = { contact(d, ContactMethod.CALL) }) { Icon(Icons.Outlined.Call, null); Text(" Call") }
                        if (d.email.isNotBlank() && ContactMethod.EMAIL in d.selectedMethods())
                            TextButton(enabled = selectedAlertId == null || selectedAlert != null, onClick = { contact(d, ContactMethod.EMAIL) }) { Icon(Icons.Outlined.Email, null); Text(" Email") }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        TextButton(onClick = { openForm(d) }) { Text("Edit") }
                        TextButton(onClick = { deleting = d }) { Text("Delete") }
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Communication history", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (history.isNotEmpty()) TextButton(onClick = { showingHistory = true }) { Text("See all") }
            }
        }
        if (history.isEmpty()) item { Text("No contact activity yet.") }
        history.firstOrNull()?.let { latest ->
            item { CommunicationHistoryCard(latest, designees) }
        }
        item { Text("") }
    }

    if (showingHistory) {
        AlertDialog(onDismissRequest = { showingHistory = false },
            title = { Text("Communication history") }, containerColor = Color.White,
            text = {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(history, key = { it.first().commId }) { entries ->
                        CommunicationHistoryCard(entries, designees)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showingHistory = false }) { Text("Close") } })
    }

    if (choosingAlert) {
        AlertDialog(onDismissRequest = { choosingAlert = false }, title = { Text("Choose an alert") },
            containerColor = Color.White, tonalElevation = 0.dp,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (alerts.isEmpty()) Text("No active alerts.", style = MaterialTheme.typography.bodyMedium)
                    alerts.forEach { alert ->
                        val reading = readingsById[alert.vitalId]
                        UpdateOption(updateTitle(alert, reading, types.firstOrNull { it.vitalTypeId == reading?.vital?.vitalTypeId }),
                            warning = true) { selectedAlertId = alert.alertId; updateChosen = true; choosingAlert = false; shareUpdateId = java.util.UUID.randomUUID().toString() }
                    }
                }
            }, confirmButton = { TextButton(onClick = { choosingAlert = false }) { Text("Close") } })
    }

    if (choosingRecipient) {
        AlertDialog(onDismissRequest = { choosingRecipient = false },
            title = { Text("Choose a trusted contact") },
            containerColor = Color.White,
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (designees.isEmpty()) {
                        Text("No trusted contacts yet.")
                        Button(onClick = { choosingRecipient = false; openForm(null) }) { Text("Add contact") }
                    }
                    designees.forEach { person ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primaryContainer = yellow, onPrimaryContainer = Color.Black)) {
                                        PhmsIconBadge(PhmsIcons.Profile)
                                    }
                                    Text(person.name, style = MaterialTheme.typography.titleMedium)
                                }
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    person.selectedMethods().forEach { method ->
                                        val available = if (method == ContactMethod.EMAIL) person.email.isNotBlank() else person.phone.isNotBlank()
                                        TextButton(enabled = available && (selectedAlertId == null || selectedAlert != null),
                                            onClick = { choosingRecipient = false; contact(person, method) }) {
                                            Icon(when (method) {
                                                ContactMethod.SMS -> Icons.Outlined.Sms
                                                ContactMethod.EMAIL -> Icons.Outlined.Email
                                                ContactMethod.CALL -> Icons.Outlined.Call
                                            }, null)
                                            Text(" " + methodLabel(method))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingRecipient = false }) { Text("Cancel") } })
    }

    pendingContact?.let { request ->
        val (person, how, linkedAlertId) = request
        val linked = alerts.firstOrNull { it.alertId == linkedAlertId }
        val destination = if (how == ContactMethod.EMAIL) person.email else person.phone
        if (!featureUnavailable) AlertDialog(onDismissRequest = { if (!busy) pendingContact = null },
            title = { Text(if (how == ContactMethod.CALL) "Call preview" else "Message preview") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium)
                    Text(destination)
                    Text(linked?.let { updatePreview(it, readingsById[it.vitalId], preferences) }
                        ?: if (linkedAlertId != null) "This alert is no longer active. Choose another update." else "Hi, this is a check-in from PHMS.")
                    Text("This preview does not send a message or place a call.")
                }
            },
            confirmButton = { TextButton(enabled = !busy && (linkedAlertId == null || linked != null),
                onClick = { featureUnavailable = true }) { Text("Continue") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingContact = null }) { Text("Cancel") } })
    }

    if (featureUnavailable) {
        AlertDialog(onDismissRequest = { featureUnavailable = false },
            containerColor = Color.White,
            title = { Text("Feature under development") },
            text = { Text("This feature is currently being updated and is not available yet. Please check back later.") },
            confirmButton = { TextButton(onClick = { featureUnavailable = false }) { Text("OK") } })
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

/** Only combine attempts explicitly linked to the same update and recipient.
 * Older records without a correlation ID stay separate rather than guessing. */
private fun communicationHistory(
    records: List<com.cse3310.projecthealthmonitoringsystem.data.entity.CommunicationLog>
): List<List<com.cse3310.projecthealthmonitoringsystem.data.entity.CommunicationLog>> =
    records.groupBy { entry ->
        val update = Regex("(?:^|; )updateId=([a-fA-F0-9-]{36})(?:$|;)")
            .find(entry.detail)?.groupValues?.get(1)
        listOf(entry.userId, entry.designeeId, entry.alertId, update ?: "record-${entry.commId}")
    }.values.map { it.sortedWith(compareByDescending<com.cse3310.projecthealthmonitoringsystem.data.entity.CommunicationLog> { entry -> entry.sentAt }
        .thenByDescending { entry -> entry.commId }) }
        .sortedWith(compareByDescending<List<com.cse3310.projecthealthmonitoringsystem.data.entity.CommunicationLog>> { it.first().sentAt }
            .thenByDescending { it.first().commId })

@Composable
private fun CommunicationHistoryCard(
    entries: List<com.cse3310.projecthealthmonitoringsystem.data.entity.CommunicationLog>,
    contacts: List<Designee>
) {
    val who = contacts.firstOrNull { it.designeeId == entries.first().designeeId }?.name ?: "Removed contact"
    OutlinedCard(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.outlinedCardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.groupBy { it.status }.forEach { (status, attempts) ->
                val methods = attempts.map { it.method }.distinct().map { methodLabel(it).lowercase(java.util.Locale.US) }
                    .joinToString(" and ")
                val message = when (status) {
                    DeliveryStatus.SENT -> "Submitted update to $who via $methods. Delivery is not confirmed."
                    DeliveryStatus.FAILED -> "Couldn't complete update to $who via $methods."
                    DeliveryStatus.OPENED -> {
                        val drafts = attempts.map { it.method }.distinct().filter { it != ContactMethod.CALL }
                            .joinToString(" and ") { methodLabel(it).lowercase(java.util.Locale.US) }
                        listOfNotNull(
                            drafts.takeIf { it.isNotBlank() }?.let { "Opened $it draft${if (attempts.map { a -> a.method }.distinct().count { it != ContactMethod.CALL } > 1) "s" else ""} for $who. Delivery is not confirmed." },
                            if (attempts.any { it.method == ContactMethod.CALL }) "Opened dialer for $who. Call completion is not confirmed." else null
                        ).joinToString(" ")
                    }
                }
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            Text(alertDate(entries.first().sentAt), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ShareChoice(title: String, selected: Boolean, modifier: Modifier, warning: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.heightIn(min = 64.dp)
        .then(Modifier.semantics { this.selected = selected }),
        color = Color.White, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp,
            if (selected) Color(0xFFFFDF48) else Color(0xFFE8E1D8))) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (warning) Icon(Icons.Outlined.WarningAmber, "Health alert", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            else if (icon != null) Icon(icon, null, tint = Color(0xFF111827), modifier = Modifier.size(20.dp))
            else if (selected) Icon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF111827), modifier = Modifier.size(20.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = Color(0xFF111827))
        }
    }
}


