package com.example.eta.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.AddItemResult
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.NoteRepository
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Note
import com.example.eta.domain.model.withExtras
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.format.formatLong
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * The Notiz-Merger: notes, and the one bridge from a note to the plan.
 *
 * "Taskify" makes a ToDo out of a note and leaves the note where it is, marked.
 * Two ways across, the user's choice each time: as a bare card into the
 * Sammelliste, to be finished in the evening like any Quick-Add, or finished on
 * the spot. Either way the **Sperrliste is asked**, as everywhere a card is
 * made — a note is not a way to re-type a banned idea.
 */
class NotesViewModel(
    private val noteRepository: NoteRepository,
    private val itemRepository: ItemRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val today: LocalDate = clock.todayIn(timeZone)

    val notes: StateFlow<List<Note>> = noteRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** ToDos a card made here can swallow as steps. */
    val foldable: StateFlow<List<Item>> = itemRepository.observeFoldCandidates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun dismissNotice() {
        _notice.value = null
    }

    fun save(id: String?, title: String, text: String) {
        viewModelScope.launch { noteRepository.save(id, title, text) }
    }

    fun delete(id: String) {
        viewModelScope.launch { noteRepository.delete(id) }
    }

    /**
     * Taskify, the short way: a bare card in the Sammelliste carrying the note.
     *
     * The note is saved first — what is taskified is what stands in the editor,
     * and a ToDo made from text that was then lost would point at nothing.
     */
    fun taskifyLater(id: String?, title: String, text: String) {
        viewModelScope.launch {
            val noteId = noteRepository.save(id, title, text)
            val item = Item.newQuickTodo(title, clock.now()).copy(note = text.ifBlank { null })
            finish(noteId, title, itemRepository.add(item), "liegt jetzt in der Sammelliste")
        }
    }

    /** Taskify, finished at once: the Listen tab's own "Neue Aufgabe", pre-filled. */
    fun taskifyNow(id: String?, title: String, text: String, name: String, attributes: TodoAttributes) {
        viewModelScope.launch {
            val noteId = noteRepository.save(id, title, text)
            val item = Item.newTodo(
                name = name,
                category = attributes.category,
                priority = attributes.priority,
                targetDate = attributes.unlockFrom,
                estimatedDuration = attributes.duration,
                now = clock.now(),
            ).copy(
                note = text.ifBlank { null },
                travelBefore = attributes.travelBefore,
                returnAfter = attributes.returnAfter,
                breakAfter = attributes.breakAfter,
                endSound = attributes.endSound,
            ).withExtras(attributes.extras)
            finish(
                noteId,
                name,
                itemRepository.add(item, attributes.subtasks, attributes.foldedItemIds),
                "liegt fertig in der Sammelliste",
            )
        }
    }

    private suspend fun finish(noteId: String, name: String, result: AddItemResult, landed: String) {
        when (result) {
            is AddItemResult.BlockedByLock -> {
                val until = result.lockedUntil.toLocalDateTime(timeZone).date.formatLong()
                _notice.value = "»$name« steht auf der Sperrliste und kann erst ab $until " +
                    "wieder notiert werden. Die Notiz ist gesichert."
            }

            is AddItemResult.Added -> {
                noteRepository.markTaskified(noteId)
                _notice.value = "»$name« $landed."
            }
        }
    }
}
