package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Context
import androidx.sqlite.db.*
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseStatus
import com.cse3310.projecthealthmonitoringsystem.data.repository.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DoseCorrectionMigrationTest {
    @Test fun versionFiveUpgradePreservesOriginalAndAddsAuditableEffectiveIntake() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val path = "schemas/com.cse3310.projecthealthmonitoringsystem.data.AppDatabase/5.json"
        val schema = JSONObject(listOf(File(path), File("app/$path")).first { it.exists() }.readText()).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(AppDatabase.DATABASE_NAME).callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val e = entities.getJSONObject(i)
                        val table = e.getString("tableName")
                        db.execSQL(e.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                        val indices = e.optJSONArray("indices") ?: JSONArray()
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    DatabaseTriggers.create(db)
                    DatabaseTriggers.createMedicationTimesAudit(db)
                    ProviderPharmacySchema.constraints(db)
                    db.execSQL("INSERT INTO User VALUES ('legacyOwner','legacy@example.com','hash','salt','question','answer','salt',0,NULL,1)")
                    db.execSQL("INSERT INTO Pharmacy VALUES (1,'legacyOwner','Fictional Pharmacy','123 Example St','Arlington','TX','76010','+12025550142','Fictional Pharmacist',1)")
                    db.execSQL("INSERT INTO Doctor (doctorId,userId,name,specialtyId,phone,email,address,providerType,clinicName) VALUES (1,'legacyOwner','Fictional Provider',NULL,'','','','Nurse practitioner','Fictional Clinic')")
                    db.execSQL("INSERT INTO Medication (medicationId,userId,name,dosage,frequency,intakeTimeMinutes,startDate,endDate,doctorId,instructions,isActive,createdAt,updatedAt,doseTimesMinutes,pharmacyId,prescriptionNumber,refillsRemaining,nextRefillAt) VALUES (1,'legacyOwner','Fictional medicine','5 mg','Daily',480,1,NULL,1,'Keep instructions',1,1,1,'480',1,'RX-42',2,NULL)")
                    db.execSQL("INSERT INTO DoseLog VALUES (1,'legacyOwner',1,1795089600000,'SCHEDULED',NULL)")
                    db.execSQL("UPDATE DoseLog SET status='TAKEN',recordedAt=1795089660000 WHERE doseId=1")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture only")
            }).build())
        helper.writableDatabase
        helper.close()
        var db = AppDatabase.build(context)
        try {
            val tx = RoomTransactionRunner(db)
            val meds = MedicationRepository(db.medicationDao(), tx) { 1795093200000 }
            val monitoring = MonitoringRepository(db.alertDao(), meds, tx) { 1795093200000 }
            val original = meds.originalDose("legacyOwner", 1)!!
            assertEquals(DoseStatus.TAKEN, original.status)
            assertEquals("RX-42", meds.getMedication("legacyOwner", 1)!!.prescriptionNumber)
            assertEquals("Nurse practitioner", db.doctorDao().getDoctor("legacyOwner", 1)!!.providerType)
            assertTrue(db.doctorDao().getPharmacy("legacyOwner", 1)!!.isPreferred)
            monitoring.recordIntake("legacyOwner", 1, original.scheduledAt, DoseStatus.SKIPPED, null, "Corrected an accidental entry", 0)
            assertEquals(original, meds.originalDose("legacyOwner", 1))
            assertEquals(DoseStatus.SKIPPED, meds.getDose("legacyOwner", 1)!!.status)
            db.close()
            db = AppDatabase.build(context)
            assertEquals(DoseStatus.SKIPPED, db.medicationDao().getDose("legacyOwner", 1)!!.status)
            assertEquals(1, db.medicationDao().correctionHistory("legacyOwner", 1).size)
            assertEquals("RX-42", db.medicationDao().getMedication("legacyOwner", 1)!!.prescriptionNumber)
            assertTrue(runCatching { db.openHelper.writableDatabase.execSQL("UPDATE DoseLog SET status='MISSED' WHERE doseId=1") }.isFailure)
            assertTrue(runCatching { db.openHelper.writableDatabase.execSQL("DELETE FROM DoseCorrection") }.isFailure)
        } finally { db.close(); context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    }
}
