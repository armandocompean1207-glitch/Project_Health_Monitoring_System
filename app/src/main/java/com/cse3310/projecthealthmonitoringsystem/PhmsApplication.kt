package com.cse3310.projecthealthmonitoringsystem

import android.app.Application
import android.content.pm.ApplicationInfo
import com.cse3310.projecthealthmonitoringsystem.data.AppDatabase
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

/**
 * Creates the database and every repository exactly once (no dependency-injection library).
 * ViewModels get a repository with `(application as PhmsApplication).medicationRepository`.
 */
class PhmsApplication : Application() {

    private val isDebuggable: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val database: AppDatabase by lazy {
        AppDatabase.build(this, seedDebugUser = isDebuggable)
    }

    private val transactionRunner by lazy { RoomTransactionRunner(database) }

    val authRepository by lazy { AuthRepository(database.userDao(), transactionRunner) }

    val maintenanceRepository by lazy {
        MaintenanceRepository(database.doctorDao(), database.lookupDao()) { owner, id ->
            try { com.cse3310.projecthealthmonitoringsystem.maintenance.AppointmentReminders.sync(this, owner, id) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { android.util.Log.w("PHMS", "Appointment reminder scheduling unavailable", failure) }
        }
    }

    val communicationRepository by lazy {
        CommunicationRepository(database.designeeDao(), database.communicationLogDao())
    }

    val medicationRepository by lazy { MedicationRepository(database.medicationDao(), transactionRunner) }

    val monitoringRepository by lazy {
        MonitoringRepository(database.alertDao(), medicationRepository, transactionRunner)
    }

    val searchRepository by lazy { SearchRepository(database.searchDao()) }

    val vitalsRepository by lazy {
        VitalsRepository(database.vitalSignDao(), monitoringRepository, transactionRunner)
    }

    val dietRepository by lazy { DietRepository(database.dietDao(), transactionRunner) }

    val notesRepository by lazy { NotesRepository(database.noteDao()) }
    val vaccinationRepository by lazy {
        com.cse3310.projecthealthmonitoringsystem.data.repository.VaccinationRepository(database.vaccinationDao())
    }
}
