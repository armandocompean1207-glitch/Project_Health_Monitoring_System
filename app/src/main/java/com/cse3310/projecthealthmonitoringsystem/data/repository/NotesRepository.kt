package com.cse3310.projecthealthmonitoringsystem.data.repository

import com.cse3310.projecthealthmonitoringsystem.data.dao.NoteDao
import com.cse3310.projecthealthmonitoringsystem.data.entity.Note
import kotlinx.coroutines.flow.Flow

/** Notes (Arpan). Private to the owner. */
class NotesRepository(
    private val noteDao: NoteDao,
    private val clock: Clock = SystemClock
) {

    /** Returns the new noteId. */
    suspend fun createNote(userId: String, title: String, body: String): Long {
        require(title.isNotBlank()) { "Title is required" }
        val now = clock()
        return noteDao.insertNote(Note(userId = userId, title = title.trim(), body = body, createdAt = now, editedAt = now))
    }

    /** Updates title and body and sets the date edited. Returns false if the note is not this user's. */
    suspend fun editNote(userId: String, noteId: Long, title: String, body: String): Boolean {
        require(title.isNotBlank()) { "Title is required" }
        val existing = noteDao.getNote(userId, noteId) ?: return false
        return noteDao.updateNote(userId, existing.copy(title = title.trim(), body = body, editedAt = clock()))
    }

    suspend fun deleteNote(userId: String, noteId: Long): Boolean = noteDao.deleteNote(userId, noteId) > 0

    suspend fun openNote(userId: String, noteId: Long): Note? = noteDao.getNote(userId, noteId)

    fun observeNotes(userId: String): Flow<List<Note>> = noteDao.observeNotes(userId)
}
