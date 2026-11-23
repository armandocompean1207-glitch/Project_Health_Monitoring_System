package com.cse3310.projecthealthmonitoringsystem.search

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextField as OutlinedTextField

import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsTextButton as TextButton
import com.cse3310.projecthealthmonitoringsystem.ui.components.PhmsCard as Card

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Vaccines
import com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination
import com.cse3310.projecthealthmonitoringsystem.data.repository.VaccinationDates
import com.cse3310.projecthealthmonitoringsystem.healthguide.VaccinationsSection
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.dao.SearchResult
import com.cse3310.projecthealthmonitoringsystem.data.repository.SearchRepository.SearchCategory
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import com.cse3310.projecthealthmonitoringsystem.notes.noteContent
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DateRange(val label: String, val days: Int?) {
    ANY("Any time", null), MONTH("30 days", 30), YEAR("Past year", 365)
}
private fun SearchCategory.label(): String = when (this) {
    SearchCategory.MEDICATIONS -> "Meds & intake"
    SearchCategory.DOCTORS_AND_VISITS -> "Doctors & visits"
    SearchCategory.VITAL_SIGNS -> "Vitals"
    SearchCategory.DIET_AND_WEIGHT -> "Diet & weight"
    SearchCategory.NOTES -> "Notes"
}
private fun resultCategory(kind: String) = when (kind) {
    "MEDICATION", "DOSE_LOG" -> SearchCategory.MEDICATIONS
    "DOCTOR", "DOCTOR_VISIT" -> SearchCategory.DOCTORS_AND_VISITS
    "VITAL_SIGN" -> SearchCategory.VITAL_SIGNS
    "DIET_ENTRY", "WEIGHT" -> SearchCategory.DIET_AND_WEIGHT
    else -> SearchCategory.NOTES
}
private fun resultLabel(kind: String) = when (kind) {
    "MEDICATION" -> "Medication"
    "DOSE_LOG" -> "Intake history"
    "DOCTOR" -> "Doctor"
    "DOCTOR_VISIT" -> "Appointment"
    "CHECKUP" -> "Annual checkup"
    "VACCINATION" -> "Vaccination"
    "VITAL_SIGN" -> "Vital reading"
    "DIET_ENTRY" -> "Meal"
    "WEIGHT" -> "Weight"
    else -> "Note"
}
private fun resultIcon(kind: String) = when (kind) {
    "MEDICATION", "DOSE_LOG" -> PhmsIcons.Medication
    "DOCTOR", "DOCTOR_VISIT", "CHECKUP" -> PhmsIcons.Appointments
    "VITAL_SIGN" -> PhmsIcons.Vitals
    "VACCINATION" -> Icons.Outlined.Vaccines
    "DIET_ENTRY" -> PhmsIcons.Diet
    "WEIGHT" -> PhmsIcons.Weight
    else -> PhmsIcons.Notes
}
private fun dateText(time: Long) =
    SimpleDateFormat("MM/dd/yyyy · h:mm a", Locale.US).format(Date(time))
private fun summary(result: SearchResult): String {
    val text = result.subtitle.orEmpty()
    return if (result.kind == "NOTE") noteContent(text) else if (result.kind == "DOSE_LOG") {
        val pieces = text.split(" · Recorded: ")
        pieces.first() + (pieces.getOrNull(1)?.toLongOrNull()?.let { " · Recorded ${dateText(it)}" } ?: "")
    } else text
}

private fun vaccinationResult(record: Vaccination) = SearchResult(
    "VACCINATION", record.vaccinationId, record.name,
    listOfNotNull("Received ${VaccinationDates.label(record.receivedDate)}",
        record.doseNumber?.let { "Dose $it" }, record.providerOrPharmacy.takeIf { it.isNotBlank() },
        record.location.takeIf { it.isNotBlank() }, record.nextDoseDate?.let { "Next dose: ${VaccinationDates.label(it)}" },
        record.notes.takeIf { it.isNotBlank() }).joinToString(" · "),
    VaccinationDates.parse(record.receivedDate))

/** Search and record previews reuse the existing account-filtered repository queries. */
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenModule: (String) -> Unit = {}) {
    val context = LocalContext.current
    val app = context.phms()
    val userId = CurrentUser.userId
    if (userId.isBlank()) return
    var vaccinationsOpen by rememberSaveable(userId) { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = vaccinationsOpen) { vaccinationsOpen = false }
    val vaccinations by remember(userId) { app.vaccinationRepository.observe(userId) }.collectAsState(emptyList())
    val history = remember(userId) { SearchHistory(context, userId) }
    var recent by remember(userId) { mutableStateOf(history.recent()) }
    var keyword by rememberSaveable(userId) { mutableStateOf("") }
    var categoryName by rememberSaveable(userId) { mutableStateOf<String?>(null) }
    val category = categoryName?.let { SearchCategory.valueOf(it) }
    var range by rememberSaveable(userId) { mutableStateOf(DateRange.ANY) }
    var requested by rememberSaveable(userId) { mutableIntStateOf(0) }
    var shortcut by rememberSaveable(userId) { mutableStateOf<String?>(null) }
    var results by remember(userId) { mutableStateOf<List<SearchResult>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<SearchResult?>(null) }

    fun search(text: String = keyword) {
        keyword = text
        shortcut = null
        history.add(text)
        recent = history.recent()
        requested++
    }

    // Cancellation prevents slower, older searches from replacing newer filters or keywords.
    LaunchedEffect(userId, keyword, categoryName, range, requested, shortcut, vaccinations) {
        if (requested == 0 && shortcut == null) return@LaunchedEffect
        results = null
        error = null
        loading = true
        try {
            delay(200)
            val now = System.currentTimeMillis()
            val from = range.days?.let { now - it * DoseTimes.DAY_MILLIS }
            results = when (shortcut) {
                "Last appointment", "Next appointment" -> {
                    val visit = if (shortcut == "Last appointment") app.searchRepository.lastDoctorVisit(userId)
                        else app.searchRepository.nextDoctorVisit(userId)
                    listOfNotNull(visit?.let { SearchResult("DOCTOR_VISIT", it.visit.visitId,
                        it.doctorName, it.visit.reason, it.visit.visitAt) })
                }
                "Past medications" -> app.searchRepository.pastMedications(userId).map {
                    SearchResult("MEDICATION", it.medicationId, it.name, it.dosage, it.endDate ?: it.startDate)
                }
                "Annual checkup" -> listOfNotNull(app.searchRepository.nextAnnualCheckup(userId)?.let {
                    SearchResult("CHECKUP", 0, "Next annual checkup", "Your profile's annual checkup date", it)
                })
                else -> ((category?.let { listOf(it) } ?: SearchCategory.values().toList())
                    .flatMap { app.searchRepository.search(userId, it, keyword, from, if (from != null) now else null) } +
                    if (category == null) vaccinations.filter { it.name.contains(keyword.trim(), ignoreCase = true) }
                        .map(::vaccinationResult).filter { from == null || it.timestamp?.let { time -> time in from..now } == true }
                    else emptyList()).sortedByDescending { it.timestamp }
            }
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { error = "Couldn't load your records. Please try again."
        } finally { loading = false }
    }

    if (vaccinationsOpen) {
        PhmsPage { VaccinationsSection(onBack = { vaccinationsOpen = false }) }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            PhmsScreenHeader("Search", "Find a reading, medicine, visit or note.", PhmsIcons.Search, onBack)
        }
        item {
            OutlinedTextField(value = keyword, onValueChange = { keyword = it; shortcut = null },
                label = { Text("Search your records") }, placeholder = { Text("Medicine, meal, note or reading") },
                leadingIcon = { Icon(PhmsIcons.Search, null) },
                trailingIcon = { if (keyword.isNotEmpty()) IconButton(onClick = { keyword = ""; shortcut = null }) {
                    Icon(Icons.Outlined.Close, "Clear search")
                } },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }))
            Button(onClick = { search() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(if (keyword.isBlank()) "Browse records" else "Search")
            }
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { categoryName = null; shortcut = null; requested++ },
                    label = { Text("All") })
                SearchCategory.values().forEach { value ->
                    FilterChip(selected = category == value,
                        onClick = { categoryName = value.name; shortcut = null; requested++ },
                        label = { Text(value.label()) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateRange.values().forEach { value ->
                    FilterChip(selected = range == value, onClick = { range = value; shortcut = null; requested++ },
                        label = { Text(value.label) })
                }
            }
        }
        item {
            Text("Quick searches", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Last appointment", "Next appointment", "Past medications", "Annual checkup").forEach { value ->
                    AssistChip(onClick = { shortcut = value; requested++ }, label = { Text(value) })
                }
            }
        }
        if (recent.isNotEmpty()) item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recent searches", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { history.clear(); recent = emptyList() }) { Text("Clear history") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { value -> AssistChip(onClick = { search(value) }, label = { Text(value) }) }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { message -> item {
            Text(message, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { requested++ }) { Text("Try again") }
        } }
        if (!loading && error == null) {
            val list = results
            if (list == null) item {
                PhmsEmptyStateCard("Find something you've saved", "Search a keyword or browse a category. Only your account's records appear here.",
                    PhmsIcons.Search, actionLabel = "Browse all records", onAction = { search("") })
            } else if (list.isEmpty()) item {
                PhmsEmptyStateCard(if (shortcut != null) "No ${shortcut!!.lowercase()} found" else "No matching records",
                    "Try a different keyword or choose Any time. You can also add a record in its section.",
                    PhmsIcons.Search, actionLabel = if (shortcut != null) "Open related section" else "Open ${category?.label() ?: "Vitals"}",
                    onAction = { onOpenModule(when (shortcut) {
                        "Last appointment", "Next appointment" -> "DOCTOR_VISIT"
                        "Past medications" -> "MEDICATION"
                        "Annual checkup" -> "CHECKUP"
                        else -> when (category) {
                        SearchCategory.MEDICATIONS -> "MEDICATION"
                        SearchCategory.DOCTORS_AND_VISITS -> "DOCTOR_VISIT"
                        SearchCategory.DIET_AND_WEIGHT -> "DIET_ENTRY"
                        SearchCategory.NOTES -> "NOTE"
                        else -> "VITAL_SIGN"
                        }
                    }) })
            } else {
                item { Text("${shortcut ?: "Results"} · ${list.size}", style = MaterialTheme.typography.titleMedium) }
                items(list, key = { "${it.kind}:${it.itemId}" }) { result ->
                    PhmsActionCard(result.title,
                        listOfNotNull(resultLabel(result.kind), summary(result).takeIf { it.isNotBlank() },
                            result.timestamp?.let(::dateText)).joinToString("\n"),
                        resultIcon(result.kind), onClick = { selected = result })
                }
            }
        }
    }
    selected?.let { hit ->
        var current by remember(hit) { mutableStateOf<SearchResult?>(null) }
        var detail by remember(hit) { mutableStateOf<String?>(null) }
        var ready by remember(hit) { mutableStateOf(false) }
        LaunchedEffect(userId, hit) {
            try {
                // Re-read under the current account; removed or foreign records cannot be opened.
                current = if (hit.kind == "VACCINATION") app.vaccinationRepository.get(userId, hit.itemId)?.let(::vaccinationResult)
                else if (hit.kind == "CHECKUP") app.searchRepository.nextAnnualCheckup(userId)?.let {
                    hit.copy(timestamp = it)
                } else app.searchRepository.search(userId, resultCategory(hit.kind), "")
                    .firstOrNull { it.kind == hit.kind && it.itemId == hit.itemId }
                detail = if (hit.kind == "NOTE") app.notesRepository.openNote(userId, hit.itemId)?.let {
                    noteContent(it.body) + "\n\nUpdated ${dateText(it.editedAt)}"
                } else current?.let(::summary)
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { detail = "Couldn't open this record. Please try again."
            } finally { ready = true }
        }
        AlertDialog(onDismissRequest = { selected = null },
            icon = { Icon(resultIcon(hit.kind), null) },
            title = { Text(current?.title ?: if (ready) "Record unavailable" else "Opening record") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!ready) CircularProgressIndicator()
                    else if (current == null) Text(detail ?: "This record may have been removed.")
                    else SelectionContainer { Column {
                        Text(resultLabel(hit.kind), style = MaterialTheme.typography.labelLarge)
                        current?.timestamp?.let { Text(dateText(it)) }
                        Text(detail.orEmpty())
                    } }
                }
            },
            confirmButton = { if (ready && current != null) TextButton(onClick = {
                selected = null
                if (hit.kind == "VACCINATION") vaccinationsOpen = true else onOpenModule(hit.kind)
            }) { Text("Open section") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } })
    }
}
