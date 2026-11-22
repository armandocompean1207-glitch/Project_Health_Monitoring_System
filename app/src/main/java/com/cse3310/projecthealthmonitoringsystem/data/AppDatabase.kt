package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cse3310.projecthealthmonitoringsystem.data.dao.AlertDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.CommunicationLogDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.DesigneeDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.DietDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.DoctorDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.LookupDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.MedicationDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.NoteDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.SearchDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.UserDao
import com.cse3310.projecthealthmonitoringsystem.data.dao.VitalSignDao
import com.cse3310.projecthealthmonitoringsystem.data.entity.Alert
import com.cse3310.projecthealthmonitoringsystem.data.entity.AuditLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.CommunicationLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.ConflictRule
import com.cse3310.projecthealthmonitoringsystem.data.entity.Designee
import com.cse3310.projecthealthmonitoringsystem.data.entity.DietEntry
import com.cse3310.projecthealthmonitoringsystem.data.entity.Doctor
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.Food
import com.cse3310.projecthealthmonitoringsystem.data.entity.Medication
import com.cse3310.projecthealthmonitoringsystem.data.entity.Note
import com.cse3310.projecthealthmonitoringsystem.data.entity.Profile
import com.cse3310.projecthealthmonitoringsystem.data.entity.Specialty
import com.cse3310.projecthealthmonitoringsystem.data.entity.User
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalSign
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import com.cse3310.projecthealthmonitoringsystem.data.entity.Weight

/** The PHMS Room database: 18 tables. Owned by Armando; ask before changing the schema. */
@Database(
    entities = [
        User::class,
        Profile::class,
        Specialty::class,
        Doctor::class,
        DoctorVisit::class,
        Medication::class,
        DoseLog::class,
        AuditLog::class,
        VitalType::class,
        VitalSign::class,
        DietEntry::class,
        Food::class,
        Weight::class,
        Note::class,
        Designee::class,
        Alert::class,
        CommunicationLog::class,
        ConflictRule::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun lookupDao(): LookupDao
    abstract fun doctorDao(): DoctorDao
    abstract fun medicationDao(): MedicationDao
    abstract fun vitalSignDao(): VitalSignDao
    abstract fun dietDao(): DietDao
    abstract fun noteDao(): NoteDao
    abstract fun designeeDao(): DesigneeDao
    abstract fun alertDao(): AlertDao
    abstract fun communicationLogDao(): CommunicationLogDao
    abstract fun searchDao(): SearchDao

    /** Creates the tamper-protection triggers and seed rows on first create. */
    class Callback(private val seedDebugUser: Boolean) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            DatabaseTriggers.create(db)
            DatabaseTriggers.createMedicationTimesAudit(db)
            SeedData.insert(db, includeDebugUser = seedDebugUser)
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            DatabaseTriggers.enableOnOpen(db)
        }
    }

    companion object {
        const val DATABASE_NAME = "phms.db"
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Profile ADD COLUMN dateOfBirth TEXT")
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Medication ADD COLUMN doseTimesMinutes TEXT")
                DatabaseTriggers.createMedicationTimesAudit(db)
            }
        }

        /**
         * Builds the database. Always use this (including in tests) so the triggers and seed data are installed.
         * Call it once, from PhmsApplication.
         */
        fun build(context: Context, inMemory: Boolean = false, seedDebugUser: Boolean = false): AppDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            }
            return builder.addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(Callback(seedDebugUser)).build()
        }
    }
}
