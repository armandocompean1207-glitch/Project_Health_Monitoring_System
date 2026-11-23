package com.cse3310.projecthealthmonitoringsystem.maintenance

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cse3310.projecthealthmonitoringsystem.auth.AccountSession
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.monitoring.AlertSender
import com.cse3310.projecthealthmonitoringsystem.monitoring.phms
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppointmentReminderOption(val bit: Int, val minutes: Long, val label: String) {
    WEEK(1, 10080, "1 week before"), DAY(2, 1440, "1 day before"),
    FIVE_HOURS(4, 300, "5 hours before"), HOUR(8, 60, "1 hour before"),
    HALF_HOUR(16, 30, "30 minutes before"), QUARTER_HOUR(32, 15, "15 minutes before")
}
internal fun appointmentReminderTimes(visit: DoctorVisit, now: Long): List<Pair<AppointmentReminderOption, Long>> =
    if (visit.cancelled) emptyList() else AppointmentReminderOption.entries.mapNotNull { option ->
        val at = visit.visitAt - option.minutes * 60_000L
        (option to at).takeIf { visit.reminderMask and option.bit != 0 &&
            visit.reminderSentMask and option.bit == 0 && at > now }
    }
internal fun appointmentReminderSummary(visit: DoctorVisit): String =
    if (visit.cancelled) "Reminders off"
    else AppointmentReminderOption.entries.filter { visit.reminderMask and it.bit != 0 }
        .joinToString { it.label }.ifBlank { "Reminders off" }

/** Appointments never create clinical alerts or contact trusted people automatically. */
object AppointmentReminders {
    const val OWNER = "appointment_owner"
    const val ID = "appointment_id"
    private const val CHANNEL = "phms_appointments"
    private fun alarm(context: Context, owner: String, id: Long, option: AppointmentReminderOption,
        at: Long = 0, version: Long = 0): PendingIntent =
        PendingIntent.getBroadcast(context, 0, Intent(context, AppointmentReminderReceiver::class.java)
            .setData(Uri.Builder().scheme("phms-appointment").authority("reminder").appendPath(owner)
                .appendPath(id.toString()).appendPath(option.bit.toString()).build())
            .putExtra(OWNER, owner).putExtra(ID, id).putExtra("bit", option.bit)
            .putExtra("appointment_at", at).putExtra("version", version),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(context: Context, owner: String, id: Long) {
        val manager = context.getSystemService(AlarmManager::class.java)
        AppointmentReminderOption.entries.forEach { manager?.cancel(alarm(context, owner, id, it)) }
        context.getSystemService(NotificationManager::class.java).cancel("appointment-$owner", id.toInt())
    }
    suspend fun sync(context: Context, owner: String, id: Long) {
        cancel(context, owner, id)
        if (AccountSession.reminderUser(context) != owner) return
        val visit = context.phms().maintenanceRepository.getDoctorVisit(owner, id) ?: return
        val manager = context.getSystemService(AlarmManager::class.java)
        AccountSession.whileActive(context, owner) {
            appointmentReminderTimes(visit, System.currentTimeMillis()).forEach { (option, at) ->
                val pending = alarm(context, owner, id, option, visit.visitAt, visit.reminderVersion)
                try {
                    if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms())
                        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                    else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                } catch (_: SecurityException) {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                }
            }
        }
    }
    suspend fun restore(context: Context, owner: String) {
        context.phms().maintenanceRepository.observeDoctorVisits(owner).first().forEach { sync(context, owner, it.visit.visitId) }
    }
    suspend fun cancelAll(context: Context, owner: String) {
        context.phms().maintenanceRepository.observeDoctorVisits(owner).first().forEach { cancel(context, owner, it.visit.visitId) }
    }
    @android.annotation.SuppressLint("MissingPermission")
    fun notify(context: Context, visit: DoctorVisit, provider: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Appointment reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = Intent(context, com.cse3310.projecthealthmonitoringsystem.MainActivity::class.java)
            .setData(Uri.Builder().scheme("phms-appointment").authority("open").appendPath(visit.userId)
                .appendPath(visit.visitId.toString()).build())
            .putExtra(OWNER, visit.userId).putExtra(ID, visit.visitId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val time = SimpleDateFormat("MM/dd/yyyy · h:mm a", Locale.US).format(Date(visit.visitAt))
        val message = "With $provider · $time"
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar).setContentTitle("Appointment reminder")
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending).setAutoCancel(true).build()
        AccountSession.whileActive(context, visit.userId) {
            manager.notify("appointment-${visit.userId}", visit.visitId.toInt(), notification)
        }
    }
    fun canNotify(context: Context): Boolean {
        if (!AlertSender.canNotify(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
}
class AppointmentRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val owner = AccountSession.reminderUser(context) ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { AppointmentReminders.restore(context, owner) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { android.util.Log.w("PHMS", "Appointment reminders could not be restored", failure) }
            finally { pending.finish() }
        }
    }
}

class AppointmentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val owner = intent.getStringExtra(AppointmentReminders.OWNER) ?: return
        if (AccountSession.reminderUser(context) != owner) return
        val id = intent.getLongExtra(AppointmentReminders.ID, -1)
        val bit = intent.getIntExtra("bit", 0)
        val option = AppointmentReminderOption.entries.firstOrNull { it.bit == bit } ?: return
        val version = intent.getLongExtra("version", -1)
        val at = intent.getLongExtra("appointment_at", -1)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = context.phms().maintenanceRepository
                val visit = repo.getDoctorVisit(owner, id) ?: return@launch
                val now = System.currentTimeMillis()
                if (visit.cancelled || visit.visitAt != at || visit.reminderVersion != version ||
                    at <= now || now < at - option.minutes * 60_000L ||
                    !AppointmentReminders.canNotify(context)) return@launch
                val provider = repo.getDoctor(owner, visit.doctorId) ?: return@launch
                if (AccountSession.reminderUser(context) == owner && repo.claimAppointmentReminder(owner, id, version, at, bit)) {
                    AppointmentReminders.notify(context, visit, provider.name)
                    val current = repo.getDoctorVisit(owner, id)
                    if (current == null) AppointmentReminders.cancel(context, owner, id)
                    else if (current.cancelled || current.reminderVersion != version) AppointmentReminders.sync(context, owner, id)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: Exception) { android.util.Log.w("PHMS", "Appointment reminder unavailable", failure)
            } finally { pending.finish() }
        }
    }
}
