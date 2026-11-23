package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.cse3310.projecthealthmonitoringsystem.data.entity.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProviderPharmacyMigrationTest {
    @Test fun versionFourUpgradeKeepsProvidersVisitsDoseHistoryAndContactConsentAndReopensSafely() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val path = "schemas/com.cse3310.projecthealthmonitoringsystem.data.AppDatabase/4.json"
        val schema = JSONObject(listOf(File(path), File("app/$path")).first { it.exists() }.readText()).getJSONObject("database")
        val legacy = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(AppDatabase.DATABASE_NAME).callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices") ?: JSONArray()
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    DatabaseTriggers.create(db)
                    DatabaseTriggers.createMedicationTimesAudit(db)
                    db.execSQL("INSERT INTO User VALUES ('legacyOwner','legacy@example.com','hash','salt','question','answer','salt',0,NULL,1)")
                    db.execSQL("INSERT INTO Doctor (doctorId,userId,name,specialtyId,phone,email,address) VALUES (1,'legacyOwner','Fictional Provider',NULL,'+12025550142','provider@example.com','123 Example St')")
                    db.execSQL("INSERT INTO DoctorVisit (visitId,userId,doctorId,visitAt,reason,notes) VALUES (1,'legacyOwner',1,1795089600000,'Routine visit','Keep notes')")
                    db.execSQL("INSERT INTO Medication (medicationId,userId,name,dosage,frequency,intakeTimeMinutes,startDate,endDate,doctorId,instructions,isActive,createdAt,updatedAt,doseTimesMinutes) VALUES (1,'legacyOwner','Fictional medicine','5 mg','Daily',480,1,NULL,1,'Keep instructions',1,1,1,'480')")
                    db.execSQL("INSERT INTO DoseLog (doseId,userId,medicationId,scheduledAt,status,recordedAt) VALUES (1,'legacyOwner',1,1795089600000,'SCHEDULED',NULL)")
                    db.execSQL("UPDATE DoseLog SET status='TAKEN', recordedAt=1795089660000 WHERE doseId=1")
                    db.execSQL("INSERT INTO Designee (designeeId,userId,name,relationship,phone,email,preferredMethod,contactMethodsMask,automaticSmsConsent) VALUES (1,'legacyOwner','Fictional Contact','','+12025550143','contact@example.com','EMAIL',7,1)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Legacy fixture only")
            }).build())
        legacy.writableDatabase
        legacy.close()
        var upgraded = AppDatabase.build(context)
        try {
            val owner = "legacyOwner"
            val provider = upgraded.doctorDao().getDoctor(owner, 1)!!
            assertEquals("", provider.providerType)
            assertEquals("", provider.clinicName)
            assertEquals("Fictional Provider", provider.name)
            assertEquals(1L, upgraded.doctorDao().getVisit(owner, 1)!!.doctorId)
            val old = upgraded.medicationDao().getMedication(owner, 1)!!
            assertNull(old.pharmacyId)
            assertNull(old.prescriptionNumber)
            assertNull(old.refillsRemaining)
            assertNull(old.nextRefillAt)
            assertEquals("480", old.doseTimesMinutes)
            val dose = upgraded.medicationDao().getDose(owner, 1)!!
            assertEquals(DoseStatus.TAKEN, dose.status)
            val contact = upgraded.designeeDao().getDesignee(owner, 1)!!
            assertEquals(7, contact.contactMethodsMask)
            assertTrue(contact.allowsAutomaticSms())
            val p = upgraded.doctorDao().insertPharmacy(Pharmacy(userId = owner, name = "Fictional Pharmacy", isPreferred = true))
            assertTrue(upgraded.medicationDao().updateMedication(owner, old.copy(pharmacyId = p, prescriptionNumber = "RX-42",
                refillsRemaining = 2, nextRefillAt = 1795176000000)))
            assertTrue(upgraded.doctorDao().updateDoctor(owner, provider.copy(providerType = "Nurse practitioner", clinicName = "Fictional Clinic")))
            upgraded.close()
            upgraded = AppDatabase.build(context)
            assertEquals("Nurse practitioner", upgraded.doctorDao().getDoctor(owner, 1)!!.providerType)
            assertEquals("RX-42", upgraded.medicationDao().getMedication(owner, 1)!!.prescriptionNumber)
            assertEquals(p, upgraded.medicationDao().getMedication(owner, 1)!!.pharmacyId)
            assertEquals(dose, upgraded.medicationDao().getDose(owner, 1))
            assertTrue(upgraded.designeeDao().getDesignee(owner, 1)!!.allowsAutomaticSms())
            assertTrue(runCatching { upgraded.openHelper.writableDatabase.execSQL("DELETE FROM DoseLog WHERE doseId=1") }.isFailure)
        } finally { upgraded.close(); context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    }
}
