package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.cse3310.projecthealthmonitoringsystem.medication.MedicationTimes
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class MedicationTimesMigrationTest {
    @Test fun versionTwoUpgradePreservesLegacyMedicationAndFinalDoseAndAddsTimeAudit() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val path = "schemas/com.cse3310.projecthealthmonitoringsystem.data.AppDatabase/2.json"
        val schema = JSONObject(listOf(File(path), File("app/$path")).first { it.exists() }.readText()).getJSONObject("database")
        val legacy = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(AppDatabase.DATABASE_NAME).callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (index in 0 until entities.length()) {
                        val entity = entities.getJSONObject(index)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices") ?: JSONArray()
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
                    DatabaseTriggers.create(db)
                    db.execSQL("INSERT INTO User VALUES ('legacyOwner', 'legacy@example.com', 'hash', 'salt', 'question', 'answer', 'salt', 0, NULL, 1)")
                    db.execSQL("INSERT INTO Profile (userId,name,phone,gender,age,weight,weightUnit,heightCm,annualCheckupAt,dateOfBirth) VALUES ('legacyOwner','Fictional Legacy','','',35,170,'LB',175,NULL,'1991-01-01')")
                    db.execSQL("INSERT INTO Medication (medicationId,userId,name,dosage,frequency,intakeTimeMinutes,startDate,endDate,doctorId,instructions,isActive,createdAt,updatedAt) VALUES (1,'legacyOwner','Fictional legacy medicine','5 mg','Twice a day',480,1,NULL,NULL,'Keep original instructions',1,1,1)")
                    db.execSQL("INSERT INTO DoseLog (doseId,userId,medicationId,scheduledAt,status,recordedAt) VALUES (1,'legacyOwner',1,1795075200000,'SCHEDULED',NULL)")
                    db.execSQL("UPDATE DoseLog SET status='TAKEN', recordedAt=1795075260000 WHERE doseId=1")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Legacy fixture only")
            }).build())
        legacy.writableDatabase
        legacy.close()
        val upgraded = AppDatabase.build(context)
        try {
            val owner = "legacyOwner"
            val med = upgraded.medicationDao().getMedication(owner, 1)!!
            assertNull(med.doseTimesMinutes)
            assertEquals(480, med.intakeTimeMinutes)
            assertEquals("Twice a day", med.frequency)
            assertEquals("Keep original instructions", med.instructions)
            assertEquals(listOf(480, 1200), MedicationTimes.forEditing(med))
            assertEquals("1991-01-01", upgraded.userDao().getProfile(owner)!!.dateOfBirth)
            val original = upgraded.medicationDao().getDose(owner, 1)!!
            assertEquals(DoseStatus.TAKEN, original.status)
            assertTrue(upgraded.medicationDao().updateMedication(owner, med.copy(doseTimesMinutes = "480,1145", updatedAt = 1795075300000)))
            assertEquals(original, upgraded.medicationDao().getDose(owner, 1))
            assertTrue(upgraded.medicationDao().getAuditLog(owner, 1).any { it.field == "doseTimesMinutes" })
            assertTrue(runCatching { upgraded.openHelper.writableDatabase.execSQL("DELETE FROM DoseLog WHERE doseId=1") }.isFailure)
            assertEquals(original, upgraded.medicationDao().getDose(owner, 1))
        } finally { upgraded.close(); context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    }
}
