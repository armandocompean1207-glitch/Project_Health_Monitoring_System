package com.cse3310.projecthealthmonitoringsystem.monitoring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import com.cse3310.projecthealthmonitoringsystem.data.repository.MonitoringRepository
import com.cse3310.projecthealthmonitoringsystem.medication.Frequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Two alarms per dose: a reminder at dose time (creates the DoseLog row), then a missed-dose
// check after the 30 minute window.
object DoseAlarms {

    const val ACTION_REMINDER = "com.cse3310.phms.ACTION_DOSE_REMINDER"
    const val ACTION_MISSED_CHECK = "com.cse3310.phms.ACTION_MISSED_CHECK"
    const val EXTRA_MEDICATION_ID = "medication_id"
    const val EXTRA_SCHEDULED_AT = "scheduled_at"
    const val EXTRA_DOSE_ID = "dose_id"

    fun nextReminderTime(med: Medication, now: Long = System.currentTimeMillis()): Long =
        DoseTimes.nextDoseTime(
            med.intakeTimeMinutes / 60,
            med.intakeTimeMinutes % 60,
            Frequency.timesPerDay(med.frequency),
            now
        )

    fun scheduleNextReminder(context: Context, med: Medication, now: Long = System.currentTimeMillis()) {
        val at = nextReminderTime(med, now)
        set(context, at, reminderIntent(context, med.medicationId, at))
    }

    fun cancelReminder(context: Context, medicationId: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(reminderIntent(context, medicationId, 0))
    }

    fun scheduleMissedCheck(context: Context, doseId: Long, at: Long) {
        set(context, maxOf(at, System.currentTimeMillis() + 5_000L), missedCheckIntent(context, doseId))
    }

    fun cancelMissedCheck(context: Context, doseId: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(missedCheckIntent(context, doseId))
    }

    private fun set(context: Context, atMillis: Long, intent: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    private fun reminderIntent(context: Context, medicationId: Long, at: Long): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_MEDICATION_ID, medicationId)
            .putExtra(EXTRA_SCHEDULED_AT, at)
        return PendingIntent.getBroadcast(
            context,
            (medicationId * 2 % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun missedCheckIntent(context: Context, doseId: Long): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java)
            .setAction(ACTION_MISSED_CHECK)
            .putExtra(EXTRA_DOSE_ID, doseId)
        return PendingIntent.getBroadcast(
            context,
            ((doseId * 2 + 1) % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.phms()
        val userId = CurrentUser.userId
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    DoseAlarms.ACTION_REMINDER -> {
                        val medicationId = intent.getLongExtra(DoseAlarms.EXTRA_MEDICATION_ID, -1L)
                        val at = intent.getLongExtra(DoseAlarms.EXTRA_SCHEDULED_AT, System.currentTimeMillis())
                        val med = app.medicationRepository.getMedication(userId, medicationId)
                        if (med != null && med.isActive) {
                            val doseId = app.medicationRepository.scheduleDose(userId, medicationId, at)
                            if (doseId != null) {
                                AlertSender.notifyUser(
                                    context,
                                    (doseId * 2 % Int.MAX_VALUE).toInt(),
                                    "Time to take ${med.name}",
                                    "${med.dosage}. Open PHMS and mark it as taken."
                                )
                                DoseAlarms.scheduleMissedCheck(
                                    context, doseId, at + MonitoringRepository.DEFAULT_MISSED_WINDOW_MS
                                )
                            }
                            DoseAlarms.scheduleNextReminder(context, med, at + 1_000L)
                        }
                    }
                    DoseAlarms.ACTION_MISSED_CHECK -> {
                        val doseId = intent.getLongExtra(DoseAlarms.EXTRA_DOSE_ID, -1L)
                        val alert = app.monitoringRepository.checkMissedDose(userId, doseId)
                        if (alert != null) AlertHandoff.send(context, alert)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}

class DoseRebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        val pending = goAsync()
        val app = context.phms()
        val userId = CurrentUser.userId
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.medicationRepository.getActiveMedications(userId)
                    .forEach { DoseAlarms.scheduleNextReminder(context, it) }
                app.monitoringRepository.checkMissedDoses(userId)
                    .forEach { AlertHandoff.send(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
