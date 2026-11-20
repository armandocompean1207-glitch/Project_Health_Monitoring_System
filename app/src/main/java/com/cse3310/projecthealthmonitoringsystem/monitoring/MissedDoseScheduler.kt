package com.cse3310.projecthealthmonitoringsystem.monitoring

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

object MissedDoseScheduler {

    const val ACTION_DOSE_TIME = "com.cse3310.phms.ACTION_DOSE_TIME"
    const val ACTION_DOSE_DEADLINE = "com.cse3310.phms.ACTION_DOSE_DEADLINE"
    const val EXTRA_DOSE_ID = "dose_id"

    fun schedule(context: Context, dose: DoseEvent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val deadline = dose.scheduledAtMillis + dose.graceMinutes * 60_000L

        if (dose.scheduledAtMillis > now) {
            setAlarm(alarmManager, dose.scheduledAtMillis, pendingIntent(context, dose.id, ACTION_DOSE_TIME))
        }
        setAlarm(
            alarmManager,
            maxOf(deadline, now + 5_000L),
            pendingIntent(context, dose.id, ACTION_DOSE_DEADLINE)
        )
    }

    fun cancel(context: Context, doseId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(context, doseId, ACTION_DOSE_TIME))
        alarmManager.cancel(pendingIntent(context, doseId, ACTION_DOSE_DEADLINE))
    }

    private fun setAlarm(alarmManager: AlarmManager, atMillis: Long, intent: PendingIntent) {
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    private fun pendingIntent(context: Context, doseId: Long, action: String): PendingIntent {
        val intent = Intent(context, MissedDoseReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_DOSE_ID, doseId)
        val offset = if (action == ACTION_DOSE_TIME) 0 else 1
        val requestCode = ((doseId * 2 + offset) % Int.MAX_VALUE).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class MissedDoseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val doseId = intent.getLongExtra(MissedDoseScheduler.EXTRA_DOSE_ID, -1L)
        if (doseId < 0) return
        MonitoringStore.init(context)
        when (intent.action) {
            MissedDoseScheduler.ACTION_DOSE_TIME -> MonitoringRepository.onDoseTime(context, doseId)
            MissedDoseScheduler.ACTION_DOSE_DEADLINE -> MonitoringRepository.onDoseDeadline(context, doseId)
        }
    }
}
