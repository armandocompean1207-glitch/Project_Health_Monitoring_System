package com.cse3310.projecthealthmonitoringsystem.medication

import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.data.repository.MonitoringRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class MedicationIntakeState(val label: String) {
    SCHEDULED("Scheduled"), DUE("Due"), TAKEN("Taken"), MISSED("Missed"),
    OVERDUE("Overdue"), NOT_RECORDED("Not recorded"), UNAVAILABLE("Unavailable")
}

/** Time alone can make an occurrence due/overdue, never taken. Final states come only from Room. */
internal fun medicationIntakeState(status: DoseStatus?, scheduledAt: Long, now: Long, medicationActive: Boolean): MedicationIntakeState =
    when {
        status == DoseStatus.TAKEN -> MedicationIntakeState.TAKEN
        status == DoseStatus.MISSED -> MedicationIntakeState.MISSED
        now < scheduledAt -> MedicationIntakeState.SCHEDULED
        scheduledAt <= now - MonitoringRepository.DEFAULT_MISSED_WINDOW_MS ->
            if (status == DoseStatus.SCHEDULED) MedicationIntakeState.OVERDUE else MedicationIntakeState.NOT_RECORDED
        status == DoseStatus.SCHEDULED || medicationActive -> MedicationIntakeState.DUE
        else -> MedicationIntakeState.UNAVAILABLE
    }

internal fun intakeDateTime(at: Long): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(Date(at))
