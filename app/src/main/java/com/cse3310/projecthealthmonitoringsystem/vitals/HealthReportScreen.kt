package com.cse3310.projecthealthmonitoringsystem.vitals

import android.content.ClipData
import android.content.Intent
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cse3310.projecthealthmonitoringsystem.auth.AccountSession
import com.cse3310.projecthealthmonitoringsystem.monitoring.CurrentUser
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import com.cse3310.projecthealthmonitoringsystem.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun HealthReportScreen(patientName: String, types: List<VitalTypeOption>,
    readings: List<VitalReadingItem>, onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = CurrentUser.userId
    val vaccinations by remember(owner) { context.phms().vaccinationRepository.observe(owner) }.collectAsState(initial = null as List<com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination>?)
    var includeVaccinations by remember(owner) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var period by remember(owner) { mutableStateOf(ReadingPeriod.MONTH) }
    var from by remember(owner) { mutableLongStateOf(dayBoundary(System.currentTimeMillis() - 29 * 86_400_000L)) }
    var to by remember(owner) { mutableLongStateOf(dayBoundary(System.currentTimeMillis(), true)) }
    var preview by remember(owner) { mutableStateOf<HealthReportSnapshot?>(null) }
    var busy by remember(owner) { mutableStateOf(false) }
    var rangeValid by remember(owner) { mutableStateOf(true) }
    var error by remember(owner) { mutableStateOf<String?>(null) }
    var confirmation by remember(owner) { mutableStateOf<String?>(null) }
    var download by remember(owner) { mutableStateOf<Pair<HealthReportSnapshot, File>?>(null) }
    fun authorized() = CurrentUser.userId == owner && AccountSession.reminderUser(context) == owner
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val request = download
        download = null
        if (uri != null && request != null) scope.launch {
            busy = true
            try {
                check(authorized() && request.first.owner == owner) { "Please sign in again." }
                withContext(Dispatchers.IO) {
                    check(authorized()) { "Please sign in again." }
                    checkNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                        request.second.inputStream().use { it.copyTo(output) }
                    }
                }
                android.widget.Toast.makeText(context, "Report saved.", android.widget.Toast.LENGTH_SHORT).show()
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { error = "Couldn't save the report. Please try again."
            } finally { busy = false }
        }
    }
    fun export(action: String) {
        val report = preview ?: return
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                check(authorized() && report.owner == owner) { "Please sign in again." }
                val file = withContext(Dispatchers.IO) { writeHealthReport(context, report) }
                check(authorized()) { "Please sign in again." }
                when (action) {
                    "Download" -> {
                        download = report to file
                        savePdf.launch("PHMS-health-report.pdf")
                    }
                    "Share" -> {
                        val uri = FileProvider.getUriForFile(context, context.packageName + ".healthreports", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("PHMS health report", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        check(AccountSession.whileActive(context, owner) {
                            context.startActivity(Intent.createChooser(intent, "Share health report"))
                        }) { "Please sign in again." }
                    }
                    "Print" -> {
                        val manager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                        check(AccountSession.whileActive(context, owner) {
                            manager.print("PHMS health report", HealthReportPrintAdapter(file, ::authorized), null)
                        }) { "Please sign in again." }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { error = failure.message ?: "Couldn't prepare the report."
            } finally { busy = false }
        }
    }
    PhmsPage {
        PhmsScreenHeader("My Health Report", icon = PhmsIcons.Vitals, onBack = onBack)
        Text("Patient-recorded health information, not a medical diagnosis.", style = MaterialTheme.typography.bodyMedium)
        ReadingRangeSelector(period, from, to, onValidityChange = { rangeValid = it; if (!it) preview = null }) { p, f, t ->
            if (!busy) { period = p; from = f; to = t; preview = null; error = null }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = includeVaccinations, enabled = !busy, onCheckedChange = {
                includeVaccinations = it; preview = null
            })
            Text("Include vaccination records", Modifier.weight(1f))
        }
        if (includeVaccinations && vaccinations == null) Text("Loading vaccination records…")
        Button(enabled = !busy && rangeValid && (!includeVaccinations || vaccinations != null) && (period != ReadingPeriod.CUSTOM || from <= to),
            modifier = Modifier.fillMaxWidth(), onClick = {
                val now = System.currentTimeMillis()
                preview = healthReportSnapshot(owner, patientName, readingWindow(period, from, to, now),
                    types, readings, now, vaccinations.orEmpty(), includeVaccinations)
                error = null
            }) { Text(if (preview == null) "Preview report" else "Refresh preview") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        preview?.let { report ->
            Text("Report preview", style = MaterialTheme.typography.titleLarge)
            Text(report.patientName, style = MaterialTheme.typography.titleMedium)
            Text("Generated ${com.cse3310.projecthealthmonitoringsystem.monitoring.alertDate(report.generatedAt)}")
            Text(report.window.label)
            Text("${report.readings.size} recorded readings", style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhmsOutlinedButton(enabled = !busy && download == null, onClick = { export("Download") }) { Text("Download PDF") }
                PhmsOutlinedButton(enabled = !busy, onClick = { confirmation = "Share" }) { Text("Share report") }
                PhmsOutlinedButton(enabled = !busy, onClick = { confirmation = "Print" }) { Text("Print report") }
            }
            if (report.includeVaccinations) {
                PhmsSectionHeader("Vaccinations")
                if (report.vaccinations.isEmpty()) Text("No vaccination records in this reporting period.")
                report.vaccinations.forEach { record ->
                    PhmsCard(colors = CardDefaults.outlinedCardColors(containerColor = androidx.compose.ui.graphics.Color.White)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            vaccinationReportLines(record).forEach { Text(it) }
                        }
                    }
                }
            }
            report.types.forEach { type ->
                val records = report.readings.filter { it.typeName == type.name }
                PhmsSectionHeader(type.name)
                if (records.isEmpty()) Text("No readings in this period.")
                else {
                    Text("Latest in reporting period: ${readingText(records.first())}", style = MaterialTheme.typography.titleMedium)
                    Text(records.first().recordedAt)
                    val points = records.asReversed().filter { chartValue(it)?.isFinite() == true }
                    if (points.size >= 2) VitalTrendPlot(points, type, Modifier.fillMaxWidth().height(130.dp), showGrid = true)
                    ReadingHistoryRows(records)
                }
            }
        }
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(if (action == "Share") "Share personal health information?" else "Print personal health information?") },
            text = { Text("This report contains your name, saved health readings and any vaccination records you selected. Only continue if you trust the receiving app, person or print service.") },
            confirmButton = { PhmsTextButton(onClick = { confirmation = null; export(action) }) { Text("Continue") } },
            dismissButton = { PhmsTextButton(onClick = { confirmation = null }) { Text("Cancel") } })
    }
}
