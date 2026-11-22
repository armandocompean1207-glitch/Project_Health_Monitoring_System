package com.cse3310.projecthealthmonitoringsystem.data

import androidx.test.core.app.ApplicationProvider
import com.cse3310.projecthealthmonitoringsystem.data.entity.Doctor
import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import com.cse3310.projecthealthmonitoringsystem.data.entity.Profile
import com.cse3310.projecthealthmonitoringsystem.data.entity.User
import com.cse3310.projecthealthmonitoringsystem.data.repository.AuthRepository
import com.cse3310.projecthealthmonitoringsystem.data.repository.CommunicationRepository
import com.cse3310.projecthealthmonitoringsystem.data.repository.DietRepository
import com.cse3310.projecthealthmonitoringsystem.data.repository.MaintenanceRepository
import com.cse3310.projecthealthmonitoringsystem.data.repository.MedicationRepository
import com.cse3310.projecthealthmonitoringsystem.data.repository.MonitoringRepository
import com.cse3310.projecthealthmonitoringsystem.data.repository.NotesRepository
import com.cse3310.projecthealthmonitoringsystem.data.repository.RoomTransactionRunner
import com.cse3310.projecthealthmonitoringsystem.data.repository.SearchRepository
import com.cse3310.projecthealthmonitoringsystem.data.repository.VitalsRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.TimeZone

/**
 * Base for in-memory Room tests. Builds the database through [AppDatabase.build] so the same triggers
 * and seed data as the app are installed, and creates two users, [A] and [B], for scoping checks.
 * Time is controlled through [now].
 */
@RunWith(RobolectricTestRunner::class)
abstract class DbTest {

    companion object {
        const val A = "userAlpha01"
        const val B = "userBravo02"
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR

        /** 2026-11-19 12:00:00 UTC */
        const val NOON = 1_795_089_600_000L
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    }

    lateinit var db: AppDatabase
    var now: Long = NOON
    val clock: () -> Long = { now }

    val tx by lazy { RoomTransactionRunner(db) }
    val auth by lazy { AuthRepository(db.userDao(), tx, clock) }
    val maintenance by lazy { MaintenanceRepository(db.doctorDao(), db.lookupDao()) }
    val communication by lazy { CommunicationRepository(db.designeeDao(), db.communicationLogDao(), clock) }
    val medications by lazy { MedicationRepository(db.medicationDao(), tx, clock) }
    val monitoring by lazy { MonitoringRepository(db.alertDao(), medications, tx, clock) }
    val search by lazy { SearchRepository(db.searchDao(), clock) { UTC } }
    val vitals by lazy { VitalsRepository(db.vitalSignDao(), monitoring, tx, clock) }
    val diet by lazy { DietRepository(db.dietDao(), tx) { UTC } }
    val notes by lazy { NotesRepository(db.noteDao(), clock) }

    open val seedDebugUser: Boolean = false

    @Before
    fun createDb() {
        db = AppDatabase.build(ApplicationProvider.getApplicationContext(), inMemory = true, seedDebugUser = seedDebugUser)
        runBlocking {
            check(auth.register(user(A), profile(A)))
            check(auth.register(user(B), profile(B)))
        }
    }

    @After
    fun closeDb() {
        db.close()
    }

    fun user(id: String) = User(
        userId = id,
        email = "$id@example.com",
        passwordHash = "hash-$id",
        passwordSalt = "salt-$id",
        securityQuestion = "First pet?",
        securityAnswerHash = "answer-hash",
        securityAnswerSalt = "answer-salt",
        createdAt = NOON
    )

    fun profile(id: String) = Profile(
        userId = id, name = "Name $id", phone = "8175550100", gender = "F", age = 30, weight = 60.0, heightCm = 165.0
    )

    fun medication(userId: String, name: String, dosage: String = "10 mg", doctorId: Long? = null) = Medication(
        userId = userId,
        name = name,
        dosage = dosage,
        frequency = "Daily",
        intakeTimeMinutes = 8 * 60,
        startDate = NOON - 30 * DAY,
        doctorId = doctorId,
        createdAt = 0,
        updatedAt = 0
    )

    suspend fun addMedication(userId: String, name: String, dosage: String = "10 mg"): Long =
        requireNotNull(medications.addMedication(userId, medication(userId, name, dosage)))

    suspend fun addDoctor(userId: String, name: String = "Dr. House"): Long =
        maintenance.addDoctor(userId, Doctor(userId = userId, name = name))

    /** Raw access for checks that must bypass the DAOs, such as the trigger tests. */
    val sql get() = db.openHelper.writableDatabase

    fun count(table: String, where: String = "1"): Int =
        sql.query("SELECT COUNT(*) FROM $table WHERE $where").use { c -> c.moveToFirst(); c.getInt(0) }
}
