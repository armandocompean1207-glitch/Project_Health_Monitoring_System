package com.cse3310.projecthealthmonitoringsystem.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cse3310.projecthealthmonitoringsystem.data.entity.ConflictRule
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalType
import com.cse3310.projecthealthmonitoringsystem.data.entity.WeightUnit

/**
 * Rows inserted when the database is first created (RoomDatabase.Callback.onCreate).
 *
 * The normal ranges and conflict pairs are for the class project only and are not medical advice.
 */
object SeedData {

    /** Debug account so teammates can work before login exists. Seeded only in debuggable builds. */
    const val DEBUG_USER_ID = "debuguser01"
    const val DEBUG_USER_EMAIL = "debug@phms.local"

    /**
     * The debug user has no password. "!" is not valid Base64, so no PBKDF2 hash can ever equal it and
     * nobody can log in as this user. Teammates set the session to [DEBUG_USER_ID] directly instead.
     */
    const val UNUSABLE_HASH = "!"

    val specialties = listOf(
        "Primary Care",
        "Cardiology",
        "Endocrinology",
        "Dermatology",
        "Gastroenterology",
        "Neurology",
        "Obstetrics and Gynecology",
        "Ophthalmology",
        "Orthopedics",
        "Pediatrics",
        "Psychiatry",
        "Pulmonology"
    )

    val vitalTypes = listOf(
        VitalType(name = "Blood Pressure", unit = "mmHg", normalMin = 90.0, normalMax = 120.0,
            secondaryLabel = "Diastolic", secondaryMin = 60.0, secondaryMax = 80.0),
        VitalType(name = "Blood Glucose", unit = "mg/dL", normalMin = 70.0, normalMax = 140.0),
        VitalType(name = "Cholesterol", unit = "mg/dL", normalMin = 0.0, normalMax = 200.0),
        VitalType(name = "Body Temperature", unit = "°C", normalMin = 36.1, normalMax = 37.2),
        VitalType(name = "Respiratory Rate", unit = "breaths/min", normalMin = 12.0, normalMax = 20.0),
        VitalType(name = "Pain Level", unit = "0-10", normalMin = 0.0, normalMax = 3.0),
        VitalType(name = "Heart Rate", unit = "bpm", normalMin = 60.0, normalMax = 100.0),
        VitalType(name = "Oxygen Saturation", unit = "%", normalMin = 95.0, normalMax = 100.0)
    )

    val conflictRules = listOf(
        ConflictRule.of("Warfarin", "Aspirin", "HIGH", "Increased bleeding risk"),
        ConflictRule.of("Warfarin", "Ibuprofen", "HIGH", "Increased bleeding risk"),
        ConflictRule.of("Warfarin", "Naproxen", "HIGH", "Increased bleeding risk"),
        ConflictRule.of("Warfarin", "Amiodarone", "HIGH", "Raises warfarin levels"),
        ConflictRule.of("Warfarin", "Fluconazole", "HIGH", "Raises warfarin levels"),
        ConflictRule.of("Sildenafil", "Nitroglycerin", "HIGH", "Severe drop in blood pressure"),
        ConflictRule.of("Sildenafil", "Isosorbide Mononitrate", "HIGH", "Severe drop in blood pressure"),
        ConflictRule.of("Simvastatin", "Clarithromycin", "HIGH", "Muscle damage risk"),
        ConflictRule.of("Simvastatin", "Amiodarone", "MEDIUM", "Muscle damage risk"),
        ConflictRule.of("Lisinopril", "Spironolactone", "MEDIUM", "High potassium"),
        ConflictRule.of("Lisinopril", "Potassium Chloride", "MEDIUM", "High potassium"),
        ConflictRule.of("Fluoxetine", "Tramadol", "HIGH", "Serotonin syndrome risk"),
        ConflictRule.of("Sertraline", "Tramadol", "HIGH", "Serotonin syndrome risk"),
        ConflictRule.of("Fluoxetine", "Phenelzine", "HIGH", "Serotonin syndrome risk"),
        ConflictRule.of("Clopidogrel", "Omeprazole", "MEDIUM", "Reduces clopidogrel effect"),
        ConflictRule.of("Methotrexate", "Trimethoprim", "HIGH", "Bone marrow suppression"),
        ConflictRule.of("Digoxin", "Amiodarone", "HIGH", "Raises digoxin levels"),
        ConflictRule.of("Levothyroxine", "Calcium Carbonate", "LOW", "Reduces levothyroxine absorption"),
        ConflictRule.of("Ciprofloxacin", "Tizanidine", "HIGH", "Severe drop in blood pressure and drowsiness"),
        ConflictRule.of("Lithium", "Ibuprofen", "MEDIUM", "Raises lithium levels")
    )

    /** Inserts all seed rows. Runs inside Room's onCreate, before any DAO exists, so it uses raw inserts. */
    fun insert(db: SupportSQLiteDatabase, includeDebugUser: Boolean, now: Long = System.currentTimeMillis()) {
        specialties.forEach { name ->
            db.insert("Specialty", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply { put("name", name) })
        }
        vitalTypes.forEach { type ->
            db.insert("VitalType", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
                put("name", type.name)
                put("unit", type.unit)
                put("normalMin", type.normalMin)
                put("normalMax", type.normalMax)
                put("secondaryLabel", type.secondaryLabel)
                put("secondaryMin", type.secondaryMin)
                put("secondaryMax", type.secondaryMax)
            })
        }
        conflictRules.forEach { rule ->
            db.insert("ConflictRule", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
                put("drugA", rule.drugA)
                put("drugB", rule.drugB)
                put("severity", rule.severity)
                put("description", rule.description)
            })
        }
        if (includeDebugUser) insertDebugUser(db, now)
    }

    private fun insertDebugUser(db: SupportSQLiteDatabase, now: Long) {
        db.insert("User", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
            put("userId", DEBUG_USER_ID)
            put("email", DEBUG_USER_EMAIL)
            put("passwordHash", UNUSABLE_HASH)
            put("passwordSalt", UNUSABLE_HASH)
            put("securityQuestion", "Debug account (no login)")
            put("securityAnswerHash", UNUSABLE_HASH)
            put("securityAnswerSalt", UNUSABLE_HASH)
            put("failedLoginAttempts", 0)
            putNull("lockedUntil")
            put("createdAt", now)
        })
        db.insert("Profile", SQLiteDatabase.CONFLICT_IGNORE, ContentValues().apply {
            put("userId", DEBUG_USER_ID)
            put("name", "Debug User")
            put("phone", "")
            put("gender", "")
            put("age", 30)
            put("weight", 70.0)
            put("weightUnit", WeightUnit.KG.name)
            put("heightCm", 170.0)
            putNull("annualCheckupAt")
        })
    }
}
