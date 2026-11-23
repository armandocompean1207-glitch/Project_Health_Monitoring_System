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
        com.cse3310.projecthealthmonitoringsystem.data.entity.Pharmacy::class,
        DoctorVisit::class,
        Medication::class,
        DoseLog::class,
        com.cse3310.projecthealthmonitoringsystem.data.entity.DoseCorrection::class,
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
        ConflictRule::class,
        com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination::class
    ],
    views = [com.cse3310.projecthealthmonitoringsystem.data.entity.EffectiveDoseLog::class],
    version = 8,
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
    abstract fun vaccinationDao(): com.cse3310.projecthealthmonitoringsystem.data.dao.VaccinationDao

    /** Creates the tamper-protection triggers and seed rows on first create. */
    class Callback(private val seedDebugUser: Boolean) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            DatabaseTriggers.create(db)
            DatabaseTriggers.createMedicationTimesAudit(db)
            ProviderPharmacySchema.constraints(db)
            DoseCorrectionSchema.constraints(db)
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

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Designee ADD COLUMN contactMethodsMask INTEGER")
                db.execSQL("ALTER TABLE Designee ADD COLUMN automaticSmsConsent INTEGER")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = ProviderPharmacySchema.migrate(db)
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) = DoseCorrectionSchema.migrate(db)
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS Vaccination (
                    vaccinationId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId TEXT NOT NULL, name TEXT NOT NULL, receivedDate TEXT NOT NULL,
                    doseNumber INTEGER, providerOrPharmacy TEXT NOT NULL, location TEXT NOT NULL,
                    nextDoseDate TEXT, notes TEXT NOT NULL, reminderEnabled INTEGER NOT NULL,
                    reminderNotifiedFor TEXT,
                    FOREIGN KEY(userId) REFERENCES User(userId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Vaccination_userId ON Vaccination(userId)")
            }
        }

        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE DoctorVisit ADD COLUMN reminderMask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE DoctorVisit ADD COLUMN reminderSentMask INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE DoctorVisit ADD COLUMN reminderVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE DoctorVisit ADD COLUMN cancelled INTEGER NOT NULL DEFAULT 0")
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
            return builder.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8).addCallback(Callback(seedDebugUser)).build()
        }
    }
}
