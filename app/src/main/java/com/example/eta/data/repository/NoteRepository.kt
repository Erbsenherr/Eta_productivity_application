package com.example.eta.data.repository

import com.example.eta.data.local.TrackingDao
import com.example.eta.domain.model.Note
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow

/** The notes of the Notiz-Merger — see [Note]. */
class NoteRepository(
    private val dao: TrackingDao,
    private val clock: Clock = Clock.System,
) {

    fun observe(): Flow<List<Note>> = dao.observeNotes()

    /**
     * Saves a note: a new one for a null [id], otherwise the one it names.
     * Returns the id, so a note made and at once turned into a ToDo can be
     * marked.
     */
    suspend fun save(id: String?, title: String, text: String): String {
        val now = clock.now()
        val existing = id?.let { dao.findNote(it) }
        val note = existing?.copy(title = title, text = text, updatedAt = now)
            ?: Note(title = title, text = text, createdAt = now, updatedAt = now)
        dao.upsert(note)
        return note.id
    }

    /** A ToDo was made of it. The note stays and says so. */
    suspend fun markTaskified(id: String) {
        val note = dao.findNote(id) ?: return
        dao.upsert(note.copy(taskifiedAt = clock.now()))
    }

    suspend fun delete(id: String) = dao.deleteNote(id)
}
