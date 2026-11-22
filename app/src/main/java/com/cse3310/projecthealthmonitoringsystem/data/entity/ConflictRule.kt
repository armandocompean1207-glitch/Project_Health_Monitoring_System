package com.cse3310.projecthealthmonitoringsystem.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Known medication conflict pair, shared by all accounts and editable in Data Maintenance.
 * Drug names are stored trimmed and lowercase with [drugA] < [drugB]; build rows with [ConflictRule.of].
 * The pairs are for the class project and are not medical advice.
 */
@Entity(tableName = "ConflictRule", indices = [Index(value = ["drugA", "drugB"], unique = true), Index("drugB")])
data class ConflictRule(
    @PrimaryKey(autoGenerate = true) val ruleId: Long = 0,
    val drugA: String,
    val drugB: String,
    val severity: String,
    val description: String
) {
    companion object {
        fun normalize(name: String): String = name.trim().lowercase()

        /** Builds a rule with names normalized and ordered so (a, b) and (b, a) map to the same row. */
        fun of(first: String, second: String, severity: String, description: String, ruleId: Long = 0): ConflictRule {
            val a = normalize(first)
            val b = normalize(second)
            require(a.isNotEmpty() && b.isNotEmpty() && a != b) { "A conflict needs two different drug names" }
            return if (a < b) ConflictRule(ruleId, a, b, severity, description)
            else ConflictRule(ruleId, b, a, severity, description)
        }
    }
}
