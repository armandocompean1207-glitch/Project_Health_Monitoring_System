package com.cse3310.projecthealthmonitoringsystem.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cse3310.projecthealthmonitoringsystem.data.entity.Note
import kotlinx.coroutines.flow.Flow

/** Notes are private: every query is scoped by userId. */
@Dao
abstract class NoteDao {

    @Insert
    abstract suspend fun insertNote(note: Note): Long

    @Update
    protected abstract suspend fun updateNoteRow(note: Note): Int

    @Transaction
    open suspend fun updateNote(userId: String, note: Note): Boolean =
        note.userId == userId && noteOwned(userId, note.noteId) && updateNoteRow(note) > 0

    @Query("DELETE FROM Note WHERE noteId = :noteId AND userId = :userId")
    abstract suspend fun deleteNote(userId: String, noteId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM Note WHERE noteId = :noteId AND userId = :userId)")
    abstract suspend fun noteOwned(userId: String, noteId: Long): Boolean

    @Query("SELECT * FROM Note WHERE noteId = :noteId AND userId = :userId")
    abstract suspend fun getNote(userId: String, noteId: Long): Note?

    @Query("SELECT * FROM Note WHERE userId = :userId ORDER BY editedAt DESC")
    abstract fun observeNotes(userId: String): Flow<List<Note>>
}
