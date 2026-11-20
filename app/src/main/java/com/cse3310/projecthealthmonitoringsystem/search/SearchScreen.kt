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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cse3310.projecthealthmonitoringsystem.monitoring.MonitoringRepository
import com.cse3310.projecthealthmonitoringsystem.monitoring.MonitoringStore

@Composable
fun SearchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { MonitoringStore.init(context) }

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var timeFilter by remember { mutableStateOf(TimeFilter.ANY) }

    MonitoringStore.doses.size
    MonitoringStore.alerts.size
    MonitoringStore.designees.size

    val results = SearchRegistry.search(query, category, timeFilter)
    val hasCriteria = query.isNotBlank() || category != null || timeFilter != TimeFilter.ANY

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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search medications, alerts, contacts…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text("Quick searches", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(onClick = {
                    query = "taken"; category = "Medication"; timeFilter = TimeFilter.PAST
                }, label = { Text("Medicine taken in the past") })
                AssistChip(onClick = {
                    query = "missed"; category = "Medication"; timeFilter = TimeFilter.ANY
                }, label = { Text("Missed doses") })
                AssistChip(onClick = {
                    query = ""; category = "Medication"; timeFilter = TimeFilter.UPCOMING
                }, label = { Text("Next doses") })
                if ("Appointments" in SearchRegistry.categories()) {
                    AssistChip(onClick = {
                        query = ""; category = "Appointments"; timeFilter = TimeFilter.PAST
                    }, label = { Text("Last doctor appointment") })
                    AssistChip(onClick = {
                        query = ""; category = "Appointments"; timeFilter = TimeFilter.UPCOMING
                    }, label = { Text("Next doctor appointment") })
                }
            }
        }

        item {
            Text("Filter by", style = MaterialTheme.typography.titleSmall)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
                SearchRegistry.categories().forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = if (category == c) null else c },
                        label = { Text(c) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeFilter.values().forEach { t ->
                    FilterChip(
                        selected = timeFilter == t,
                        onClick = { timeFilter = t },
                        label = {
                            Text(
                                when (t) {
                                    TimeFilter.ANY -> "Any time"
                                    TimeFilter.PAST -> "Past"
                                    TimeFilter.UPCOMING -> "Upcoming"
                                }
                            )
                        }
                    )
                }
            }
            if (hasCriteria) {
                TextButton(onClick = { query = ""; category = null; timeFilter = TimeFilter.ANY }) {
                    Text("Clear")
                }
            }
        }

        item {
            val countText = when (results.size) {
                0 -> "No results."
                1 -> "1 result"
                else -> "${results.size} results"
            }
            Text(countText, style = MaterialTheme.typography.titleMedium)
        }

        items(results) { result ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(result.category.uppercase(), style = MaterialTheme.typography.labelSmall)
                    Text(result.title, style = MaterialTheme.typography.titleMedium)
                    if (result.detail.isNotBlank()) Text(result.detail)
                    result.dateMillis?.let {
                        Text(MonitoringRepository.formatTime(it), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Text("") }
    }
}
