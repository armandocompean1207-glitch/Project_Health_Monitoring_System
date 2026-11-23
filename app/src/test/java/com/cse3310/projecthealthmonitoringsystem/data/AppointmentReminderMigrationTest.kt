package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Context
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.data.repository.MaintenanceRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppointmentReminderMigrationTest {
    @Test fun sevenToEightPreservesRecordsAndDoesNotEnableUnrequestedLegacyReminders() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val path = "schemas/com.cse3310.projecthealthmonitoringsystem.data.AppDatabase/7.json"
        val schema = JSONObject(listOf(File(path), File("app/$path")).first { it.exists() }.readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(AppDatabase.DATABASE_NAME).callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices") ?: JSONArray()
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                    }
                    val views = schema.getJSONArray("views")
                    for (i in 0 until views.length()) {
                        val view = views.getJSONObject(i)
                        db.execSQL(view.getString("createSql").replace("${'$'}{VIEW_NAME}", view.getString("viewName")))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    DatabaseTriggers.create(db)
                    DatabaseTriggers.createMedicationTimesAudit(db)
                    ProviderPharmacySchema.constraints(db)
                    DoseCorrectionSchema.constraints(db)
                    db.execSQL("INSERT INTO User VALUES ('legacyOwner','legacy@example.com','hash','salt','question','answer','salt',0,NULL,1)")
                    db.execSQL("INSERT INTO Doctor (doctorId,userId,name,specialtyId,phone,email,address,providerType,clinicName) VALUES (1,'legacyOwner','Fictional Provider',NULL,'','','','','')")
                    db.execSQL("INSERT INTO DoctorVisit VALUES (1,'legacyOwner',1,1795089600000,'Keep reason','Keep notes')")
                    db.execSQL("INSERT INTO Vaccination VALUES (1,'legacyOwner','Fictional vaccine','2026-11-19',NULL,'','',NULL,'Keep vaccination',0,NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture only")
            }).build())
        helper.writableDatabase
        helper.close()
        var db = AppDatabase.build(context)
        try {
            val old = db.doctorDao().getVisit("legacyOwner", 1)!!
            assertEquals("Keep notes", old.notes)
            assertEquals(0, old.reminderMask)
            assertEquals(0, old.reminderSentMask)
            assertFalse(old.cancelled)
            assertEquals("Keep vaccination", db.vaccinationDao().get("legacyOwner", 1)!!.notes)
            val repo = MaintenanceRepository(db.doctorDao(), db.lookupDao())
            val id = repo.addDoctorVisit("legacyOwner", DoctorVisit(userId = "legacyOwner", doctorId = 1, visitAt = 1796089600000))!!
            assertEquals(10, repo.getDoctorVisit("legacyOwner", id)!!.reminderMask)
            assertTrue(repo.claimAppointmentReminder("legacyOwner", id, 0, 1796089600000, 8))
            db.close()
            db = AppDatabase.build(context)
            assertEquals(8, db.doctorDao().getVisit("legacyOwner", id)!!.reminderSentMask)
            assertEquals(old, db.doctorDao().getVisit("legacyOwner", 1))
        } finally { db.close(); context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    }
}
