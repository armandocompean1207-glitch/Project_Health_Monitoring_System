package com.cse3310.projecthealthmonitoringsystem.auth

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import com.cse3310.projecthealthmonitoringsystem.data.SeedData
import com.cse3310.projecthealthmonitoringsystem.data.entity.Profile
import com.cse3310.projecthealthmonitoringsystem.data.entity.User
import com.cse3310.projecthealthmonitoringsystem.data.repository.AuthRepository
import com.cse3310.projecthealthmonitoringsystem.monitoring.*
import com.cse3310.projecthealthmonitoringsystem.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Authentication checks share the existing persistent repository lockout rules. */
class Authentication(private val repository: AuthRepository) {
    private val mutex = Mutex()
    suspend fun login(identifier: String, password: CharArray): String = mutex.withLock {
        try {
            val user = repository.findAccount(identifier) ?: throw IllegalArgumentException("User ID or password is incorrect.")
            check(!repository.isLocked(user.userId)) { "Too many attempts. Please wait five minutes before trying again." }
            val valid = withContext(Dispatchers.Default) { PasswordHasher.verify(password, user.passwordHash, user.passwordSalt) }
            if (!valid) {
                val state = repository.recordFailedLogin(user.userId)
                throw IllegalArgumentException(if (state.lockedUntil != null) "Account locked for five minutes." else "User ID or password is incorrect.")
            }
            repository.recordSuccessfulLogin(user.userId)
            user.userId
        } finally { password.fill('\u0000') }
    }
    suspend fun register(id: String, email: String, name: String, password: CharArray,
        question: String, answer: CharArray): Boolean = try {
        require(id.matches(Regex("[A-Za-z0-9]{8,32}"))) { "User ID must contain 8–32 letters or numbers." }
        require(android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { "Enter a valid email address." }
        com.cse3310.projecthealthmonitoringsystem.validation.HealthValidation.name(name)?.let { throw IllegalArgumentException(it) }
        require(password.size >= 12 && password.size <= 128) { "Use a password of 12–128 characters." }
        require(question.isNotBlank() && question.length <= 500 && answer.isNotEmpty() && answer.size <= 500) { "Enter a security question and answer." }
        val (hash, answerHash) = withContext(Dispatchers.Default) {
            PasswordHasher.hash(password) to PasswordHasher.hash(answer)
        }
        repository.register(User(userId = id, email = email.trim(), passwordHash = hash.value, passwordSalt = hash.salt,
            securityQuestion = question.trim(), securityAnswerHash = answerHash.value, securityAnswerSalt = answerHash.salt,
            createdAt = System.currentTimeMillis()),
            Profile(userId = id, name = name.trim(), phone = "", gender = "", age = 0, heightCm = 0.0, weight = 0.0))
    } finally { password.fill('\u0000'); answer.fill('\u0000') }
}

/** The saved ID authorizes background reminders only. UI sign-in is never restored from preferences. */
object AccountSession {
    private const val PREFS = "reminder_account"
    fun reminderUser(context: Context): String? {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("owner", null)
        return id?.takeIf { it != SeedData.DEBUG_USER_ID || isDebug(context) }
    }
    fun isDebug(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    @Synchronized
    private fun setReminderUser(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("owner", id).commit()
    }
    @Synchronized
    fun whileActive(context: Context, id: String, action: () -> Unit): Boolean {
        if (reminderUser(context) != id) return false
        action()
        return true
    }
    private suspend fun cancel(context: Context, id: String) {
        val repo = context.phms().medicationRepository
        repo.observeAllMedications(id).first().forEach { DoseAlarms.cancelReminder(context, it.medicationId) }
        repo.observeDoseLog(id, 0, Long.MAX_VALUE).first().forEach { DoseAlarms.cancelMissedCheck(context, it.dose.doseId) }
        try { com.cse3310.projecthealthmonitoringsystem.healthguide.VaccinationReminders.cancelAll(context, id) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { android.util.Log.w("PHMS", "Vaccination reminders remain blocked for the signed-out account", failure) }
        try { com.cse3310.projecthealthmonitoringsystem.maintenance.AppointmentReminders.cancelAll(context, id) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { android.util.Log.w("PHMS", "Appointment reminders remain blocked for the signed-out account", failure) }
        context.getSystemService(NotificationManager::class.java).cancelAll()
    }
    suspend fun start(context: Context, id: String, demo: Boolean = false) {
        require(id != SeedData.DEBUG_USER_ID || (demo && isDebug(context))) { "Demo access is unavailable." }
        val previous = reminderUser(context)
        setReminderUser(context, null)
        CurrentUser.clear()
        if (previous != null) cancel(context, previous)
        setReminderUser(context, id)
        try {
            context.phms().medicationRepository.getActiveMedications(id).forEach { DoseAlarms.scheduleNextReminder(context, it) }
            context.phms().medicationRepository.observeDoseLog(id, 0, Long.MAX_VALUE).first()
                .filter { it.dose.status == com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus.SCHEDULED }
                .forEach { DoseAlarms.scheduleMissedCheck(context, it.dose.doseId,
                    it.dose.scheduledAt + com.cse3310.projecthealthmonitoringsystem.data.repository.MonitoringRepository.DEFAULT_MISSED_WINDOW_MS, id) }
            try { com.cse3310.projecthealthmonitoringsystem.healthguide.VaccinationReminders.restore(context, id) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { android.util.Log.w("PHMS", "Optional vaccination reminders could not be restored", failure) }
            try { com.cse3310.projecthealthmonitoringsystem.maintenance.AppointmentReminders.restore(context, id) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { android.util.Log.w("PHMS", "Appointment reminders could not be restored", failure) }
            CurrentUser.signIn(id)
        } catch (e: Exception) { setReminderUser(context, null); throw e }
    }
    suspend fun logout(context: Context) = withContext(kotlinx.coroutines.NonCancellable) {
        val id = reminderUser(context)
        setReminderUser(context, null)
        CurrentUser.clear()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        if (id != null) cancel(context, id)
    }
}
