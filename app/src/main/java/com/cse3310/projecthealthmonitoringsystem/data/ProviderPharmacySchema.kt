package com.cse3310.projecthealthmonitoringsystem.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Supplemental constraints work for both Room creation and the additive migration. */
internal object ProviderPharmacySchema {
    const val CREATE_PHARMACY = """CREATE TABLE IF NOT EXISTS Pharmacy (
        pharmacyId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId TEXT NOT NULL,
        name TEXT NOT NULL, street TEXT NOT NULL, city TEXT NOT NULL, state TEXT NOT NULL,
        zip TEXT NOT NULL, phone TEXT NOT NULL, pharmacistName TEXT NOT NULL, isPreferred INTEGER NOT NULL,
        FOREIGN KEY(userId) REFERENCES User(userId) ON UPDATE NO ACTION ON DELETE CASCADE)"""

    fun constraints(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS pharmacy_preferred_insert
            BEFORE INSERT ON Pharmacy WHEN NEW.isPreferred = 1 BEGIN
            UPDATE Pharmacy SET isPreferred = 0 WHERE userId = NEW.userId AND isPreferred = 1; END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS pharmacy_preferred_update
            BEFORE UPDATE OF isPreferred, userId ON Pharmacy WHEN NEW.isPreferred = 1 BEGIN
            UPDATE Pharmacy SET isPreferred = 0 WHERE userId = NEW.userId
            AND pharmacyId <> NEW.pharmacyId AND isPreferred = 1; END""")
        // Defense in depth alongside repository ownership checks.
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS medication_pharmacy_owner_insert
            BEFORE INSERT ON Medication WHEN NEW.pharmacyId IS NOT NULL AND NOT EXISTS
            (SELECT 1 FROM Pharmacy WHERE pharmacyId = NEW.pharmacyId AND userId = NEW.userId)
            BEGIN SELECT RAISE(ABORT, 'Pharmacy must belong to the medication owner'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS medication_pharmacy_owner_update
            BEFORE UPDATE OF pharmacyId, userId ON Medication WHEN NEW.pharmacyId IS NOT NULL AND NOT EXISTS
            (SELECT 1 FROM Pharmacy WHERE pharmacyId = NEW.pharmacyId AND userId = NEW.userId)
            BEGIN SELECT RAISE(ABORT, 'Pharmacy must belong to the medication owner'); END""")
    }

    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Doctor ADD COLUMN providerType TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Doctor ADD COLUMN clinicName TEXT NOT NULL DEFAULT ''")
        db.execSQL(CREATE_PHARMACY)
        db.execSQL("CREATE INDEX IF NOT EXISTS index_Pharmacy_userId ON Pharmacy(userId)")
        db.execSQL("ALTER TABLE Medication ADD COLUMN pharmacyId INTEGER REFERENCES Pharmacy(pharmacyId) ON DELETE SET NULL")
        db.execSQL("ALTER TABLE Medication ADD COLUMN prescriptionNumber TEXT")
        db.execSQL("ALTER TABLE Medication ADD COLUMN refillsRemaining INTEGER")
        db.execSQL("ALTER TABLE Medication ADD COLUMN nextRefillAt INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_Medication_pharmacyId ON Medication(pharmacyId)")
        constraints(db)
    }
}
