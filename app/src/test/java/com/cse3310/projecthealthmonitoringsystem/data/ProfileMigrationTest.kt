package com.cse3310.projecthealthmonitoringsystem.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ProfileMigrationTest {
    @Test fun versionOneDatabaseUpgradesWithoutLosingProfileOrTriggers() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val relative = "schemas/com.cse3310.projecthealthmonitoringsystem.data.AppDatabase/1.json"
        val schema = JSONObject(listOf(File(relative), File("app/$relative")).first { it.exists() }.readText()).getJSONObject("database")
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(AppDatabase.DATABASE_NAME).callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(old: androidx.sqlite.db.SupportSQLiteDatabase) {
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("${'$'}{TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
            old.execSQL("INSERT INTO User VALUES ('migrationUser', 'migration@example.com', 'hash', 'salt', 'question', 'answer', 'salt', 0, NULL, 1)")
            old.execSQL("INSERT INTO Profile (userId,name,phone,gender,age,weight,weightUnit,heightCm,annualCheckupAt) VALUES ('migrationUser','Fictional Migration','2025550142','',45,180.125,'LB',177.81,12345)")
            DatabaseTriggers.create(old)
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Legacy fixture only")
            }).build())
        helper.writableDatabase
        helper.close()
        val updated = AppDatabase.build(context)
        try {
            val profile = updated.userDao().getProfile("migrationUser")!!
            assertNull(profile.dateOfBirth)
            assertEquals(45, profile.age)
            assertEquals(180.125, profile.weight, 0.0)
            assertEquals(177.81, profile.heightCm, 0.0)
            assertEquals("LB", profile.weightUnit.name)
            assertEquals(12345L, profile.annualCheckupAt)
            // Full Room open validates the migrated schema; the original protection triggers must survive.
            updated.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger'").use {
                assertTrue(it.moveToFirst()); assertTrue(it.getInt(0) > 0)
            }
        } finally { updated.close(); context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    }
}
