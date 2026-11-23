package com.cse3310.projecthealthmonitoringsystem.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.cse3310.projecthealthmonitoringsystem.data.entity.EFFECTIVE_DOSE_SQL

internal object DoseCorrectionSchema {
    fun constraints(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS dosecorrection_freeze_original BEFORE UPDATE ON DoseLog
            WHEN EXISTS (SELECT 1 FROM DoseCorrection WHERE doseId=OLD.doseId)
            BEGIN SELECT RAISE(ABORT, 'Original intake with corrections is immutable'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS dosecorrection_no_update BEFORE UPDATE ON DoseCorrection
            BEGIN SELECT RAISE(ABORT, 'Intake corrections are append-only'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS dosecorrection_no_delete BEFORE DELETE ON DoseCorrection
            BEGIN SELECT RAISE(ABORT, 'Intake corrections cannot be deleted'); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS dosecorrection_validate BEFORE INSERT ON DoseCorrection
            WHEN NOT EXISTS (SELECT 1 FROM DoseLog WHERE doseId=NEW.doseId AND userId=NEW.userId)
            OR NEW.status NOT IN ('SCHEDULED','TAKEN','MISSED','SKIPPED') OR NEW.changedAt <= 0
            OR length(trim(NEW.reason)) = 0
            OR (NEW.status = 'TAKEN' AND (NEW.actualTakenAt IS NULL OR NEW.actualTakenAt <= 0 OR NEW.actualTakenAt > NEW.changedAt))
            OR (NEW.status <> 'TAKEN' AND NEW.actualTakenAt IS NOT NULL)
            BEGIN SELECT RAISE(ABORT, 'Invalid intake correction'); END""")
    }
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS DoseCorrection (
            correctionId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId TEXT NOT NULL,
            doseId INTEGER NOT NULL, status TEXT NOT NULL, actualTakenAt INTEGER, changedAt INTEGER NOT NULL, reason TEXT NOT NULL,
            FOREIGN KEY(doseId) REFERENCES DoseLog(doseId) ON UPDATE NO ACTION ON DELETE NO ACTION,
            FOREIGN KEY(userId) REFERENCES User(userId) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_DoseCorrection_doseId ON DoseCorrection(doseId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_DoseCorrection_userId ON DoseCorrection(userId)")
        db.execSQL("CREATE VIEW IF NOT EXISTS `EffectiveDoseLog` AS $EFFECTIVE_DOSE_SQL")
        constraints(db)
    }
}
