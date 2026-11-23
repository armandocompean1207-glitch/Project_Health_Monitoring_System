package com.cse3310.projecthealthmonitoringsystem.vitals

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.toArgb

internal data class HealthReportSnapshot(
    val owner: String, val patientName: String, val generatedAt: Long,
    val window: ReadingWindow, val types: List<VitalTypeOption>, val readings: List<VitalReadingItem>,
    val includeVaccinations: Boolean = false,
    val vaccinations: List<com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination> = emptyList()
)
internal fun healthReportSnapshot(owner: String, name: String, window: ReadingWindow,
    types: List<VitalTypeOption>, readings: List<VitalReadingItem>, now: Long,
    vaccinations: List<com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination> = emptyList(),
    includeVaccinations: Boolean = false): HealthReportSnapshot =
    HealthReportSnapshot(owner, name.ifBlank { "Name not provided" }, now, window,
        types.filter { supportedVital(it.name) }.toList(),
        readings.filter { supportedVital(it.typeName) && window.contains(it) }
            .sortedWith(compareByDescending<VitalReadingItem> { it.timestamp }.thenByDescending { it.readingId }).toList(),
        includeVaccinations,
        if (!includeVaccinations) emptyList() else vaccinations.filter { record ->
            val dates = com.cse3310.projecthealthmonitoringsystem.data.repository.VaccinationDates
            record.userId == owner && dates.parse(record.receivedDate) != null &&
                (window.start == Long.MIN_VALUE || record.receivedDate >= dates.iso(window.start)) &&
                record.receivedDate <= dates.iso(minOf(window.end, now))
        }.sortedWith(compareByDescending<com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination> { it.receivedDate }
            .thenByDescending { it.vaccinationId }).toList())

/** An account-scoped, immutable export. Display units are supplied by the existing Vitals route. */
internal fun writeHealthReport(context: Context, report: HealthReportSnapshot): File {
    val directory = File(context.cacheDir, "health_reports").apply { mkdirs() }
    val file = File(directory, "PHMS-health-report-${java.util.UUID.randomUUID()}.pdf")
    val document = PdfDocument()
    val ink = Color.rgb(15, 23, 42)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 11f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var y = 0f
    fun finish() {
        page?.let {
            it.canvas.drawText("PHMS · Patient-recorded information · Page $pageNumber", 36f, 816f, text)
            document.finishPage(it)
        }
        page = null
    }
    fun next() {
        finish()
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        page!!.canvas.drawColor(Color.WHITE)
        page!!.canvas.drawRect(36f, 24f, 559f, 28f, Paint().apply { color = Color.rgb(6, 95, 70) })
        page!!.canvas.drawText("PHMS · My Health Report", 36f, 48f, text)
        y = 72f
    }
    fun room(height: Float) { if (page == null || y + height > 790f) next() }
    fun line(value: String, heading: Boolean = false) {
        text.textSize = if (heading) 15f else 11f
        text.typeface = Typeface.create("sans-serif", if (heading) Typeface.BOLD else Typeface.NORMAL)
        value.split('\n').forEach { paragraph ->
            var remaining = paragraph
            do {
                room(if (heading) 23f else 17f)
                val count = if (remaining.isEmpty()) 0 else text.breakText(remaining, true, 523f, null).coerceAtLeast(1)
                page!!.canvas.drawText(remaining.take(count), 36f, y, text)
                y += if (heading) 23f else 17f
                remaining = remaining.drop(count)
            } while (remaining.isNotEmpty())
        }
        text.textSize = 11f
        text.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    try {
        next()
        val date = SimpleDateFormat("MM/dd/yyyy · h:mm a", Locale.US)
        line("Patient: ${report.patientName}", true)
        line("Generated: ${date.format(Date(report.generatedAt))}")
        line("Reporting period: ${report.window.label}")
        line("Patient-recorded health information. Not a medical diagnosis.")
        line("Abnormal indicators reflect existing saved records, not a new clinical assessment.")
        report.types.forEach { type ->
            y += 12
            room(80f)
            line(type.name, true)
            val records = report.readings.filter { it.typeName == type.name }
            if (records.isEmpty()) line("No recorded readings in this period.")
            else {
                line("Latest in reporting period: ${readingText(records.first())}")
                line(records.first().recordedAt)
                val points = records.asReversed().filter { chartValue(it)?.isFinite() == true }
                if (points.size >= 2) {
                    room(170f)
                    drawReportChart(page!!.canvas, points, type, y, text)
                    y += 166f
                }
                line("Measurement history", true)
                records.forEach { item ->
                    line("${readingText(item)} · ${item.recordedAt}${if (item.isAbnormal) " · ABNORMAL" else ""}")
                    item.contextLabel?.let { line("Context: $it") }
                }
            }
        }
        if (report.includeVaccinations) {
            y += 12
            line("Vaccinations", true)
            if (report.vaccinations.isEmpty()) line("No vaccination records in this reporting period.")
            report.vaccinations.forEach { record ->
                room(70f)
                vaccinationReportLines(record).forEach { line(it) }
                y += 8
            }
        }
        finish()
        file.outputStream().use { document.writeTo(it) }
        return file
    } catch (failure: Throwable) {
        file.delete()
        throw failure
    } finally { document.close() }
}

private fun drawReportChart(canvas: Canvas, points: List<VitalReadingItem>, type: VitalTypeOption,
    top: Float, label: Paint) {
    val scale = roundedScale(points, type.name)
    val (primary, secondary) = vitalChartColors(type.name)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2f; strokeCap = Paint.Cap.ROUND }
    val left = 83f
    val width = 464f
    val height = 104f
    fun point(item: VitalReadingItem, value: Double): PointF =
        chartPosition(item, value, scale, width, height, 5f).let { PointF(left + it.first, top + it.second) }
    for (i in 0..4) {
        val yy = top + 5 + i * (height - 10) / 4
        paint.color = Color.rgb(235, 238, 241)
        canvas.drawLine(left, yy, left + width, yy, paint)
        canvas.drawText(java.math.BigDecimal.valueOf(scale.high - i * (scale.high - scale.low) / 4)
            .setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(), 36f, yy + 4, label)
    }
    if (type.name.contains("heart", true)) {
        points.groupBy { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.timestamp)) }.values.forEach { day ->
            val low = point(day.first(), day.minOf { chartValue(it)!! })
            val high = point(day.first(), day.maxOf { chartValue(it)!! })
            paint.color = primary.toArgb(); paint.strokeWidth = 5f
            if (low.y == high.y) canvas.drawCircle(low.x, low.y, 2.5f, paint)
            else canvas.drawLine(low.x, low.y, high.x, high.y, paint)
        }
    } else {
        listOf(false, true).forEach { second ->
            if (!second || type.secondValueLabel != null) {
                val series = points.mapNotNull { item -> chartValue(item, second)?.takeIf(Double::isFinite)?.let { point(item, it) } }
                paint.color = (if (second) secondary else primary).toArgb()
                if (!type.name.contains("glucose", true)) series.zipWithNext().forEach { (a, b) ->
                    canvas.drawLine(a.x, a.y, b.x, b.y, paint)
                }
                series.forEach { canvas.drawCircle(it.x, it.y, 2.5f, paint) }
            }
        }
    }
    val date = SimpleDateFormat("MM/dd/yyyy", Locale.US)
    canvas.drawText(date.format(Date(points.first().timestamp)), left, top + 126f, label)
    val end = date.format(Date(points.last().timestamp))
    canvas.drawText(end, left + width - label.measureText(end), top + 126f, label)
    canvas.drawText(type.unit + if (type.secondValueLabel != null) " · Coral: systolic · Blue: diastolic" else "",
        36f, top + 148f, label)
}

internal fun vaccinationReportLines(record: com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination): List<String> {
    val dates = com.cse3310.projecthealthmonitoringsystem.data.repository.VaccinationDates
    return listOfNotNull(
        record.name,
        "Received: ${dates.label(record.receivedDate)}",
        record.doseNumber?.let { "Dose: $it" },
        record.providerOrPharmacy.takeIf { it.isNotBlank() }?.let { "Provider or pharmacy: $it" },
        record.location.takeIf { it.isNotBlank() }?.let { "Location: $it" },
        record.nextDoseDate?.let { "User-entered next dose date: ${dates.label(it)}" },
        record.notes.takeIf { it.isNotBlank() }?.let { "Notes: $it" }
    )
}

internal class HealthReportPrintAdapter(private val file: File, private val authorized: () -> Boolean) : PrintDocumentAdapter() {
    override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: android.os.Bundle?) {
        if (cancellationSignal?.isCanceled == true) callback.onLayoutCancelled()
        else if (!authorized()) callback.onLayoutFailed("Please sign in again.")
        else callback.onLayoutFinished(PrintDocumentInfo.Builder("PHMS health report.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), true)
    }
    override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
        if (cancellationSignal?.isCanceled == true) { callback.onWriteCancelled(); return }
        if (!authorized()) { callback.onWriteFailed("Please sign in again."); return }
        try {
            file.inputStream().use { source -> java.io.FileOutputStream(destination.fileDescriptor).use { source.copyTo(it) } }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (_: Exception) { callback.onWriteFailed("Couldn't print the report.") }
    }
}
