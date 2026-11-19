package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.cse3310.projecthealthmonitoringsystem.data.entity.DietEntry
import com.cse3310.projecthealthmonitoringsystem.data.entity.Doctor
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoctorVisit
import com.cse3310.projecthealthmonitoringsystem.data.entity.DoseLog
import com.cse3310.projecthealthmonitoringsystem.data.entity.Food
import com.cse3310.projecthealthmonitoringsystem.data.entity.VitalSign

/** Read-only shapes returned by JOIN queries. Each is filled by one SQL query (no N+1). */

data class DoctorWithSpecialty(
    @Embedded val doctor: Doctor,
    val specialtyName: String?
)

data class DoctorVisitWithDoctor(
    @Embedded val visit: DoctorVisit,
    val doctorName: String
)

data class DoseWithMedication(
    @Embedded val dose: DoseLog,
    val medicationName: String,
    val dosage: String
)

data class VitalSignWithType(
    @Embedded val vital: VitalSign,
    val typeName: String,
    val unit: String
)

data class DietEntryWithFoods(
    @Embedded val entry: DietEntry,
    @Relation(parentColumn = "dietEntryId", entityColumn = "dietEntryId")
    val foods: List<Food>
)

/** Two of the user's active medications that match a ConflictRule. */
data class MedicationConflict(
    val ruleId: Long,
    val severity: String,
    val description: String,
    val medicationIdA: Long,
    val medicationNameA: String,
    val medicationIdB: Long,
    val medicationNameB: String
)

/** An active medication that conflicts with a drug the user is about to add. */
data class CandidateConflict(
    val ruleId: Long,
    val severity: String,
    val description: String,
    val conflictingMedicationId: Long,
    val conflictingMedicationName: String
)

/**
 * One search hit. [kind] says which table it came from (see [SearchResultKind]) and [itemId] is that
 * row's primary key, so the screen can open the right detail view.
 */
data class SearchResult(
    val kind: String,
    val itemId: Long,
    val title: String,
    val subtitle: String?,
    val timestamp: Long?
)

object SearchResultKind {
    const val MEDICATION = "MEDICATION"
    const val DOCTOR = "DOCTOR"
    const val DOCTOR_VISIT = "DOCTOR_VISIT"
    const val VITAL_SIGN = "VITAL_SIGN"
    const val DIET_ENTRY = "DIET_ENTRY"
    const val WEIGHT = "WEIGHT"
    const val NOTE = "NOTE"
}
