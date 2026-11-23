package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Context
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.cse3310.projecthealthmonitoringsystem.data.entity.Vaccination
import com.cse3310.projecthealthmonitoringsystem.data.repository.VaccinationRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class VaccinationMigrationTest {
    @Test fun versionSixUpgradePreservesExistingRecordsAndVaccinationsPersistAfterReopen() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val path = "schemas/com.cse3310.projecthealthmonitoringsystem.data.AppDatabase/6.json"
        val schema = JSONObject(listOf(File(path), File("app/$path")).first { it.exists() }.readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(AppDatabase.DATABASE_NAME).callback(object : SupportSQLiteOpenHelper.Callback(6) {
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
                    db.execSQL("INSERT INTO Medication (medicationId,userId,name,dosage,frequency,intakeTimeMinutes,startDate,endDate,doctorId,instructions,isActive,createdAt,updatedAt,doseTimesMinutes) VALUES (1,'legacyOwner','Fictional medicine','5 mg','Daily',480,1,NULL,NULL,'Keep instructions',1,1,1,'480')")
                    db.execSQL("INSERT INTO DoseLog VALUES (1,'legacyOwner',1,1795089600000,'SCHEDULED',NULL)")
                    db.execSQL("UPDATE DoseLog SET status='TAKEN',recordedAt=1795089660000 WHERE doseId=1")
                    db.execSQL("INSERT INTO Note (noteId,userId,title,body,createdAt,editedAt) VALUES (1,'legacyOwner','Keep note','Keep content',1,1)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture only")
            }).build())
        helper.writableDatabase
        helper.close()
        var db = AppDatabase.build(context)
        try {
            val original = db.medicationDao().getDose("legacyOwner", 1)!!
            assertEquals("Keep instructions", db.medicationDao().getMedication("legacyOwner", 1)!!.instructions)
            val id = VaccinationRepository(db.vaccinationDao()) { 1795176000000 }.save("legacyOwner",
                Vaccination(userId = "legacyOwner", name = "Fictional vaccine", receivedDate = "2026-11-19"))
            db.close()
            db = AppDatabase.build(context)
            assertEquals("Fictional vaccine", db.vaccinationDao().get("legacyOwner", id)!!.name)
            assertEquals(original, db.medicationDao().getDose("legacyOwner", 1))
            db.openHelper.readableDatabase.query("SELECT body FROM Note WHERE noteId=1").use {
                assertTrue(it.moveToFirst()); assertEquals("Keep content", it.getString(0))
            }
            assertTrue(runCatching { db.openHelper.writableDatabase.execSQL("DELETE FROM DoseLog WHERE doseId=1") }.isFailure)
        } finally { db.close(); context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    }
}
