package com.cse3310.projecthealthmonitoringsystem.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.data.dao.SearchResult
import com.cse3310.projecthealthmonitoringsystem.data.repository.SearchRepository.SearchCategory
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseTimes
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import kotlinx.coroutines.launch

private enum class DateRange(val label: String, val days: Int?) {
    ANY("Any time", null),
    MONTH("Past 30 days", 30),
    YEAR("Past year", 365)
}

private fun SearchCategory.label(): String = when (this) {
    SearchCategory.MEDICATIONS -> "Medications"
    SearchCategory.DOCTORS_AND_VISITS -> "Doctors & visits"
    SearchCategory.VITAL_SIGNS -> "Vital signs"
    SearchCategory.DIET_AND_WEIGHT -> "Diet & weight"
    SearchCategory.NOTES -> "Notes"
}

@Composable
fun SearchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.phms()
    val userId = CurrentUser.userId
    val scope = rememberCoroutineScope()
    remember { SearchHistory.init(context) }

    var keyword by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SearchCategory.MEDICATIONS) }
    var range by remember { mutableStateOf(DateRange.ANY) }
    var results by remember { mutableStateOf<List<SearchResult>?>(null) }
    var shortcutTitle by remember { mutableStateOf<String?>(null) }
    var shortcutLines by remember { mutableStateOf<List<String>>(emptyList()) }

    fun runSearch(text: String = keyword) {
        keyword = text
        shortcutTitle = null
        val to = System.currentTimeMillis()
        val from = range.days?.let { to - it * DoseTimes.DAY_MILLIS }
        scope.launch {
            results = app.searchRepository.search(userId, category, text, from, if (from != null) to else null)
            SearchHistory.add(text)
        }
    }

    fun showShortcut(title: String, load: suspend () -> List<String>) {
        results = null
        scope.launch {
            shortcutLines = load()
            shortcutTitle = title
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) { Text("← Back to More") }
            Text("Search", style = MaterialTheme.typography.headlineMedium)
            Text("Find anything you have saved in PHMS.", style = MaterialTheme.typography.bodyMedium)
        }

        item {
            Text("Quick searches", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = {
                    showShortcut("Last doctor visit") {
                        listOfNotNull(app.searchRepository.lastDoctorVisit(userId)?.let {
                            "${it.doctorName} on ${DoseTimes.formatTime(it.visit.visitAt)}"
                        })
                    }
                }, label = { Text("Last doctor visit") })
                AssistChip(onClick = {
                    showShortcut("Next doctor visit") {
                        listOfNotNull(app.searchRepository.nextDoctorVisit(userId)?.let {
                            "${it.doctorName} on ${DoseTimes.formatTime(it.visit.visitAt)}"
                        })
                    }
                }, label = { Text("Next doctor visit") })
                AssistChip(onClick = {
                    showShortcut("Past medications") {
                        app.searchRepository.pastMedications(userId).map { "${it.name} ${it.dosage}" }
                    }
                }, label = { Text("Past medications") })
                AssistChip(onClick = {
                    showShortcut("Next annual checkup") {
                        listOfNotNull(app.searchRepository.nextAnnualCheckup(userId)?.let { DoseTimes.formatTime(it) })
                    }
                }, label = { Text("Next annual checkup") })
            }
        }

        item {
            Text("Search in", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchCategory.values().forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label()) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateRange.values().forEach { r ->
                    FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) })
                }
            }
        }

        item {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                label = { Text("Keyword (leave empty to see everything)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { runSearch() }, modifier = Modifier.padding(top = 8.dp)) { Text("Search") }
        }

        if (SearchHistory.recent.isNotEmpty()) {
            item {
                Row {
                    Text("Recent searches", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { SearchHistory.clear() }) { Text("Clear history") }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SearchHistory.recent.toList().forEach { past ->
                        AssistChip(onClick = { runSearch(past) }, label = { Text(past) })
                    }
                }
            }
        }

        shortcutTitle?.let { title ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (shortcutLines.isEmpty()) {
                            Text("Nothing found.", color = MaterialTheme.colorScheme.error)
                        }
                        shortcutLines.forEach { Text(it) }
                    }
                }
            }
        }

        results?.let { list ->
            item {
                if (list.isEmpty()) {
                    Text(
                        "No records match your search. Try another keyword, type or date range.",
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(if (list.size == 1) "1 result" else "${list.size} results", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(list, key = { it.kind + it.itemId }) { result ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(result.kind.replace('_', ' '), style = MaterialTheme.typography.labelSmall)
                        Text(result.title, style = MaterialTheme.typography.titleMedium)
                        result.subtitle?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        result.timestamp?.let {
                            Text(DoseTimes.formatTime(it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { Text("") }
    }
}
