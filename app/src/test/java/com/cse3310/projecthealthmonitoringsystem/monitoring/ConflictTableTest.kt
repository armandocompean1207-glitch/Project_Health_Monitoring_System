package com.cse3310.projecthealthmonitoringsystem.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictTableTest {

    @Test
    fun knownPairIsFlaggedInEitherOrder() {
        assertEquals(1, ConflictTable.findConflicts("Aspirin 81mg", listOf("Warfarin")).size)
        assertEquals(1, ConflictTable.findConflicts("warfarin", listOf("aspirin")).size)
    }

    @Test
    fun safeCombinationIsNotFlagged() {
        assertTrue(ConflictTable.findConflicts("Metformin", listOf("Vitamin D")).isEmpty())
    }

    @Test
    fun sameMedicationIsNotAConflictWithItself() {
        assertTrue(ConflictTable.findConflicts("Warfarin", listOf("warfarin")).isEmpty())
    }

    @Test
    fun blankNameIsIgnored() {
        assertTrue(ConflictTable.findConflicts("  ", listOf("Warfarin")).isEmpty())
    }
}
