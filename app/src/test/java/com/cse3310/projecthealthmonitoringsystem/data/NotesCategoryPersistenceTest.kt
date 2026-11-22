package com.cse3310.projecthealthmonitoringsystem.data

import com.cse3310.projecthealthmonitoringsystem.notes.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NotesCategoryPersistenceTest : DbTest() {
    @Test fun categoriesAndContentSurviveRepositoryRoundTrip() = runBlocking<Unit> {
        val content = "Recipe\n[PHMS:HEALTH]\nKeep all user text intact."
        val id = notes.createNote(A, "Dinner", categorizedContent(NoteCategory.RECIPES, content))
        val stored = notes.openNote(A, id)!!
        assertEquals(NoteCategory.RECIPES, noteCategory(stored.body))
        assertEquals(content, noteContent(stored.body))
        assertNull(notes.openNote(B, id))
        assertFalse(notes.editNote(B, id, "Other", "Changed"))
        assertFalse(notes.deleteNote(B, id))
        assertTrue(notes.editNote(A, id, "Questions", categorizedContent(NoteCategory.HEALTH, "Ask doctor")))
        val updated = notes.observeNotes(A).first().single()
        assertEquals(stored.createdAt, updated.createdAt)
        assertEquals(NoteCategory.HEALTH, noteCategory(updated.body))
        assertEquals("Ask doctor", noteContent(updated.body))
    }

    @Test fun legacyContentRemainsUnchangedAndGeneral() {
        val content = "Existing private note\nWith multiple lines"
        assertEquals(NoteCategory.GENERAL, noteCategory(content))
        assertEquals(content, noteContent(content))
        assertEquals(content, noteContent(categorizedContent(NoteCategory.GENERAL, content)))
    }
}
