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
    const val EXTRA_USER_ID = "user_id"

    fun nextReminderTime(med: Medication, now: Long = System.currentTimeMillis()): Long =
        com.cse3310.projecthealthmonitoringsystem.medication.nextScheduledIntake(med, now) ?: Long.MAX_VALUE

    fun scheduleNextReminder(context: Context, med: Medication, now: Long = System.currentTimeMillis()) {
        if (!med.isActive || com.cse3310.projecthealthmonitoringsystem.auth.AccountSession.reminderUser(context) != med.userId) return
        val at = com.cse3310.projecthealthmonitoringsystem.medication.nextScheduledIntake(med, now)
        if (at == null) { cancelReminder(context, med.medicationId); return }
        set(context, at, reminderIntent(context, med.medicationId, at, med.userId))
    }

    fun cancelReminder(context: Context, medicationId: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(reminderIntent(context, medicationId, 0))
    }

    fun scheduleMissedCheck(context: Context, doseId: Long, at: Long, userId: String = CurrentUser.userId) {
        set(context, maxOf(at, System.currentTimeMillis() + 5_000L), missedCheckIntent(context, doseId, userId))
    }

    fun cancelMissedCheck(context: Context, doseId: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(missedCheckIntent(context, doseId))
    }

    fun cancelDoseNotification(context: Context, doseId: Long) {
        context.getSystemService(android.app.NotificationManager::class.java)?.cancel((doseId * 2 % Int.MAX_VALUE).toInt())
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

    private fun reminderIntent(context: Context, medicationId: Long, at: Long, userId: String = CurrentUser.userId): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_MEDICATION_ID, medicationId)
            .putExtra(EXTRA_SCHEDULED_AT, at)
            .putExtra(EXTRA_USER_ID, userId)
        return PendingIntent.getBroadcast(
            context,
            (medicationId * 2 % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun missedCheckIntent(context: Context, doseId: Long, userId: String = CurrentUser.userId): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java)
            .setAction(ACTION_MISSED_CHECK)
            .putExtra(EXTRA_DOSE_ID, doseId)
            .putExtra(EXTRA_USER_ID, userId)
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
        val userId = com.cse3310.projecthealthmonitoringsystem.auth.AccountSession.reminderUser(context) ?: return
        if (intent.getStringExtra(DoseAlarms.EXTRA_USER_ID)?.let { it != userId } == true) return
        val pending = goAsync()
        val app = context.phms()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    DoseAlarms.ACTION_REMINDER -> {
                        val medicationId = intent.getLongExtra(DoseAlarms.EXTRA_MEDICATION_ID, -1L)
                        val at = intent.getLongExtra(DoseAlarms.EXTRA_SCHEDULED_AT, System.currentTimeMillis())
                        val med = app.medicationRepository.getMedication(userId, medicationId)
                        if (med != null && com.cse3310.projecthealthmonitoringsystem.medication.isScheduledIntake(med, at) &&
                            (med.endDate == null || System.currentTimeMillis() <= med.endDate)) {
                            val doseId = app.medicationRepository.scheduleDose(userId, medicationId, at)
                            if (doseId != null &&
                                app.medicationRepository.getDose(userId, doseId)?.status == com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus.SCHEDULED) {
                                com.cse3310.projecthealthmonitoringsystem.auth.AccountSession.whileActive(context, userId) {
                                    AlertSender.notifyUser(context, (doseId * 2 % Int.MAX_VALUE).toInt(),
                                        "Time to take ${med.name}", "${med.dosage}. Open PHMS and mark it as taken.")
                                }
                                DoseAlarms.scheduleMissedCheck(context, doseId,
                                    at + MonitoringRepository.DEFAULT_MISSED_WINDOW_MS, userId)
                                // A confirmation can race this broadcast. Never leave a stale reminder
                                // or missed check for an occurrence that became final while posting.
                                if (app.medicationRepository.getDose(userId, doseId)?.status !=
                                    com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus.SCHEDULED) {
                                    DoseAlarms.cancelDoseNotification(context, doseId)
                                    DoseAlarms.cancelMissedCheck(context, doseId)
                                }
                            }
                            DoseAlarms.scheduleNextReminder(context, med, maxOf(at + 1_000L, System.currentTimeMillis()))
                        } else if (med != null) {
                            // Ignore an old broadcast after editing dates/times, then restore the current schedule.
                            DoseAlarms.scheduleNextReminder(context, med)
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
        val userId = com.cse3310.projecthealthmonitoringsystem.auth.AccountSession.reminderUser(context) ?: return
        val pending = goAsync()
        val app = context.phms()
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
