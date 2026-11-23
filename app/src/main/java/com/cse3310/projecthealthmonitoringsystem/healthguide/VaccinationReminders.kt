package com.cse3310.projecthealthmonitoringsystem.healthguide

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cse3310.projecthealthmonitoringsystem.auth.AccountSession
import com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination
import com.cse3310.projecthealthmonitoringsystem.data.repository.VaccinationDates
import com.cse3310.projecthealthmonitoringsystem.monitoring.AlertSender
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/** Optional reminders for the user's entered next-dose date; never a computed vaccine schedule. */
object VaccinationReminders {
    private const val CHANNEL = "phms_vaccinations"
    private fun pending(context: Context, owner: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(context, 0,
            Intent(context, VaccinationReminderReceiver::class.java)
                .setData(Uri.Builder().scheme("phms-vaccination").authority("reminder").appendPath(owner).appendPath(id.toString()).build())
                .putExtra("owner", owner).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(context: Context, owner: String, id: Long) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context, owner, id))
        context.getSystemService(NotificationManager::class.java)?.cancel("vaccination-$owner", id.toInt())
    }
    fun schedule(context: Context, record: Vaccination, now: Long = System.currentTimeMillis()) {
        cancel(context, record.userId, record.vaccinationId)
        val date = record.nextDoseDate ?: return
        val at = VaccinationDates.reminderAt(date) ?: return
        if (!record.reminderEnabled || record.reminderNotifiedFor == date || date < VaccinationDates.iso(now)) return
        AccountSession.whileActive(context, record.userId) {
            context.getSystemService(AlarmManager::class.java)?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, maxOf(at, now + 5_000), pending(context, record.userId, record.vaccinationId))
        }
    }
    suspend fun restore(context: Context, owner: String) {
        context.phms().vaccinationRepository.observe(owner).first().forEach { schedule(context, it) }
    }
    suspend fun cancelAll(context: Context, owner: String) {
        context.phms().vaccinationRepository.observe(owner).first().forEach { cancel(context, owner, it.vaccinationId) }
    }
    @android.annotation.SuppressLint("MissingPermission")
    fun notify(context: Context, record: Vaccination) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Vaccination reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, com.cse3310.projecthealthmonitoringsystem.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Vaccination reminder")
            .setContentText("${record.name} · Next dose date: ${VaccinationDates.label(record.nextDoseDate!!)}")
            .setContentIntent(open).setAutoCancel(true).build()
        AccountSession.whileActive(context, record.userId) {
            manager.notify("vaccination-${record.userId}", record.vaccinationId.toInt(), notification)
        }
    }
}
class VaccinationReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val owner = intent.getStringExtra("owner") ?: return
        if (AccountSession.reminderUser(context) != owner) return
        val id = intent.getLongExtra("id", -1)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = context.phms().vaccinationRepository
                val record = repo.get(owner, id) ?: return@launch
                val date = record.nextDoseDate ?: return@launch
                val at = VaccinationDates.reminderAt(date) ?: return@launch
                if (!record.reminderEnabled || at > System.currentTimeMillis() ||
                    VaccinationDates.iso(System.currentTimeMillis()) != date ||
                    !AlertSender.canNotify(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return@launch
                if (Build.VERSION.SDK_INT >= 26 && context.getSystemService(NotificationManager::class.java)
                        .getNotificationChannel("phms_vaccinations")?.importance == NotificationManager.IMPORTANCE_NONE) return@launch
                if (AccountSession.reminderUser(context) == owner && repo.claimReminder(owner, id, date)) {
                    VaccinationReminders.notify(context, record)
                    val current = repo.get(owner, id)
                    if (current == null) VaccinationReminders.cancel(context, owner, id)
                    else if (!current.reminderEnabled || current.nextDoseDate != date) VaccinationReminders.schedule(context, current)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) {
                android.util.Log.w("PHMS", "Vaccination reminder could not be shown", failure)
            } finally { pending.finish() }
        }
    }
}
