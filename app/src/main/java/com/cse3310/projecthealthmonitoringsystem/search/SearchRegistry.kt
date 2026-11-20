package com.cse3310.projecthealthmonitoringsystem.search

import com.cse3310.projecthealthmonitoringsystem.monitoring.AlertType
import com.cse3310.projecthealthmonitoringsystem.monitoring.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.monitoring.MonitoringRepository
import com.cse3310.projecthealthmonitoringsystem.monitoring.MonitoringStore

data class SearchResult(
    val category: String,
    val title: String,
    val detail: String,
    val dateMillis: Long? = null
)

interface SearchSource {
    val category: String
    fun all(): List<SearchResult>
}

enum class TimeFilter { ANY, PAST, UPCOMING }

object SearchRegistry {

    private val sources = linkedMapOf<String, SearchSource>()

    init {
        register(MedicationHistorySource)
        register(AlertHistorySource)
        register(DesigneeSource)
    }

    fun register(source: SearchSource) {
        sources[source.category] = source
    }

    fun categories(): List<String> = sources.keys.toList()

    fun search(
        text: String,
        category: String? = null,
        timeFilter: TimeFilter = TimeFilter.ANY,
        now: Long = System.currentTimeMillis()
    ): List<SearchResult> {
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }

        val matches = sources.values
            .filter { category == null || it.category == category }
            .flatMap { source -> runCatching { source.all() }.getOrDefault(emptyList()) }
            .filter { result ->
                val haystack = "${result.category} ${result.title} ${result.detail}".lowercase()
                words.all { haystack.contains(it) }
            }
            .filter { result ->
                val date = result.dateMillis
                when (timeFilter) {
                    TimeFilter.ANY -> true
                    TimeFilter.PAST -> date != null && date <= now
                    TimeFilter.UPCOMING -> date != null && date > now
                }
            }

        return when (timeFilter) {
            TimeFilter.UPCOMING -> matches.sortedBy { it.dateMillis }
            else -> matches.sortedWith(compareByDescending<SearchResult> { it.dateMillis ?: Long.MIN_VALUE })
        }
    }
}

private object MedicationHistorySource : SearchSource {
    override val category = "Medication"
    override fun all() = MonitoringStore.doses.map { dose ->
        val status = when (dose.status) {
            DoseStatus.TAKEN -> "taken"
            DoseStatus.MISSED -> "missed"
            DoseStatus.SCHEDULED -> "scheduled"
        }
        SearchResult(
            category = category,
            title = "${dose.medicationName} ($status)",
            detail = "Due ${MonitoringRepository.formatTime(dose.scheduledAtMillis)}" +
                (dose.takenAtMillis?.let { ", taken ${MonitoringRepository.formatTime(it)}" } ?: ""),
            dateMillis = dose.takenAtMillis ?: dose.scheduledAtMillis
        )
    }
}

private object AlertHistorySource : SearchSource {
    override val category = "Alerts"
    override fun all() = MonitoringStore.alerts.map { alert ->
        SearchResult(
            category = category,
            title = if (alert.type == AlertType.MISSED_DOSE) "Missed dose alert" else "Medication conflict alert",
            detail = alert.message,
            dateMillis = alert.createdAtMillis
        )
    }
}

private object DesigneeSource : SearchSource {
    override val category = "Contacts"
    override fun all() = MonitoringStore.designees.map { d ->
        SearchResult(
            category = category,
            title = d.name,
            detail = listOf(d.relationship, d.phone, d.email).filter { it.isNotBlank() }.joinToString(" · ")
        )
    }
}
