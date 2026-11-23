package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.*

/** Explicit patient entries/corrections are append-only; the original DoseLog is never rewritten. */
@Entity(tableName = "DoseCorrection", foreignKeys = [
    ForeignKey(entity = DoseLog::class, parentColumns = ["doseId"], childColumns = ["doseId"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = User::class, parentColumns = ["userId"], childColumns = ["userId"], onDelete = ForeignKey.NO_ACTION)
], indices = [Index("doseId"), Index("userId")])
data class DoseCorrection(
    @PrimaryKey(autoGenerate = true) val correctionId: Long = 0,
    val userId: String,
    val doseId: Long,
    val status: DoseStatus,
    val actualTakenAt: Long? = null,
    val changedAt: Long,
    val reason: String
)

const val EFFECTIVE_DOSE_SQL = """SELECT d.doseId, d.userId, d.medicationId, d.scheduledAt,
    COALESCE(c.status, d.status) AS status,
    CASE WHEN c.correctionId IS NULL THEN d.recordedAt
         WHEN c.status = 'TAKEN' THEN c.actualTakenAt
         WHEN c.status IN ('MISSED', 'SKIPPED') THEN c.changedAt ELSE NULL END AS recordedAt
    FROM DoseLog d LEFT JOIN DoseCorrection c ON c.doseId = d.doseId AND c.userId = d.userId
    AND c.correctionId = (SELECT MAX(revision.correctionId) FROM DoseCorrection revision WHERE revision.doseId = d.doseId AND revision.userId = d.userId)"""

@DatabaseView(value = EFFECTIVE_DOSE_SQL, viewName = "EffectiveDoseLog")
data class EffectiveDoseLog(val doseId: Long, val userId: String, val medicationId: Long,
    val scheduledAt: Long, val status: DoseStatus, val recordedAt: Long?)
