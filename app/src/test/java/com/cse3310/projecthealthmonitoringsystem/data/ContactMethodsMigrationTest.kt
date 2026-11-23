package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.cse3310.projecthealthmonitoringsystem.data.entity.ContactMethod
import com.cse3310.projecthealthmonitoringsystem.data.entity.Designee
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ContactMethodsMigrationTest {
    @Test fun versionThreeContactsAndConsentSurviveUpgradeAndMultiSelectionsSurviveReopening() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val path = "schemas/com.cse3310.projecthealthmonitoringsystem.data.AppDatabase/3.json"
        val schema = JSONObject(listOf(File(path), File("app/$path")).first { it.exists() }.readText()).getJSONObject("database")
        val legacy = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(AppDatabase.DATABASE_NAME).callback(object : SupportSQLiteOpenHelper.Callback(3) {
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
                    DatabaseTriggers.createMedicationTimesAudit(db)
                    db.execSQL("INSERT INTO User VALUES ('legacyOwner', 'legacy@example.com', 'hash', 'salt', 'question', 'answer', 'salt', 0, NULL, 1)")
                    db.execSQL("INSERT INTO Designee VALUES (1,'legacyOwner','Fictional SMS','Family','+12025550142','fictional@example.com','SMS')")
                    db.execSQL("INSERT INTO Designee VALUES (2,'legacyOwner','Fictional Email','','','fictional2@example.com','EMAIL')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Legacy fixture only")
            }).build())
        legacy.writableDatabase
        legacy.close()
        var upgraded = AppDatabase.build(context)
        try {
            val original = upgraded.designeeDao().getDesignee("legacyOwner", 1)!!
            assertNull(original.contactMethodsMask)
            assertNull(original.automaticSmsConsent)
            assertTrue(original.allowsAutomaticSms())
            assertEquals(ContactMethod.entries.toSet(), original.selectedMethods())
            assertFalse(upgraded.designeeDao().getDesignee("legacyOwner", 2)!!.allowsAutomaticSms())
            assertNull(upgraded.designeeDao().getDesignee("anotherOwner", 1))
            val edited = original.copy(contactMethodsMask = Designee.methodsMask(setOf(ContactMethod.EMAIL, ContactMethod.CALL)),
                automaticSmsConsent = false)
            assertTrue(upgraded.designeeDao().updateDesignee("legacyOwner", edited))
            upgraded.close()
            upgraded = AppDatabase.build(context)
            val reloaded = upgraded.designeeDao().getDesignee("legacyOwner", 1)!!
            assertEquals(setOf(ContactMethod.EMAIL, ContactMethod.CALL), reloaded.selectedMethods())
            assertFalse(reloaded.allowsAutomaticSms())
            assertEquals(original.name, reloaded.name)
            assertEquals(original.phone, reloaded.phone)
            assertEquals(original.email, reloaded.email)
        } finally { upgraded.close(); context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    }
}
