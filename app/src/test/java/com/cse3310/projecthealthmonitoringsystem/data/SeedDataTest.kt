package com.cse3310.projecthealthmonitoringsystem.data

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedDataTest : DbTest() {

    override val seedDebugUser = true

    @Test
    fun lookupTablesAreSeeded() = runBlocking<Unit> {
        val lookups = db.lookupDao()
        assertEquals(SeedData.specialties.size, lookups.getSpecialties().size)
        assertEquals(20, lookups.getConflictRules().size)

        val types = lookups.getVitalTypes().associateBy { it.name }
        for (name in listOf("Blood Pressure", "Blood Glucose", "Cholesterol", "Body Temperature",
            "Respiratory Rate", "Pain Level", "Heart Rate")) {
            assertNotNull("missing vital type $name", types[name])
        }
        val bp = types.getValue("Blood Pressure")
        assertEquals(90.0, bp.normalMin, 0.0)
        assertEquals(120.0, bp.normalMax, 0.0)
        assertEquals(60.0, bp.secondaryMin!!, 0.0)
        assertEquals(80.0, bp.secondaryMax!!, 0.0)
        types.values.forEach { assertTrue("${it.name} range", it.normalMin <= it.normalMax) }
    }

    @Test
    fun conflictPairsAreNormalizedAndOrdered() = runBlocking<Unit> {
        db.lookupDao().getConflictRules().forEach {
            assertEquals(it.drugA, it.drugA.trim().lowercase())
            assertEquals(it.drugB, it.drugB.trim().lowercase())
            assertTrue("${it.drugA} < ${it.drugB}", it.drugA < it.drugB)
        }
    }

    @Test
    fun debugUserExistsButCannotHaveAPasswordMatch() = runBlocking<Unit> {
        val debug = db.userDao().getUser(SeedData.DEBUG_USER_ID)
        assertNotNull(debug)
        assertEquals(SeedData.UNUSABLE_HASH, debug!!.passwordHash)
        assertEquals(SeedData.UNUSABLE_HASH, debug.securityAnswerHash)
        assertNotNull(db.userDao().getProfile(SeedData.DEBUG_USER_ID))
    }

    @Test
    fun triggersAreInstalled() {
        val names = sql.query("SELECT name FROM sqlite_master WHERE type = 'trigger' ORDER BY name").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertEquals(
            listOf(
                "doselog_no_delete",
                "doselog_update_once",
                "medication_audit_dosage",
                "medication_audit_dose_times",
                "medication_audit_intake_time",
                "medication_audit_name"
            ),
            names
        )
    }

    @Test
    fun releaseBuildHasNoDebugUser() = runBlocking<Unit> {
        val release = AppDatabase.build(ApplicationProvider.getApplicationContext(), inMemory = true, seedDebugUser = false)
        try {
            assertNull(release.userDao().getUser(SeedData.DEBUG_USER_ID))
            assertEquals(20, release.lookupDao().getConflictRules().size)
        } finally {
            release.close()
        }
    }
}
