package com.example.erik_iteration_2.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erik_iteration_2.data.repository.AddItemResult
import com.example.erik_iteration_2.data.repository.ItemRepository
import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** What a quick-add has to say back. */
sealed interface QuickAddFeedback {
    data class Added(val name: String) : QuickAddFeedback
    data class AddedRecurring(val name: String) : QuickAddFeedback
    data class Blocked(val name: String, val until: LocalDate) : QuickAddFeedback
}

/**
 * The two quick-adds: a note for the Sammelliste, and a note that is meant to
 * repeat.
 *
 * Both take **a name and nothing else**. That is the whole point of a quick-add —
 * the thought is caught now, the questions are answered in the evening, in the
 * concretizing step that already exists for exactly this. The only difference
 * between the two is which kind of card the evening will then ask about.
 *
 * Its own view model rather than a corner of the dashboard's, because the
 * home-screen widget shows exactly the same panel. Two copies of this would
 * drift the first time either was touched.
 */
class QuickAddViewModel(
    private val itemRepository: ItemRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _feedback = MutableStateFlow<QuickAddFeedback?>(null)
    val feedback: StateFlow<QuickAddFeedback?> = _feedback.asStateFlow()

    fun quickAdd(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _feedback.value = when (val result = itemRepository.addQuickTodo(trimmed)) {
                is AddItemResult.Added -> QuickAddFeedback.Added(trimmed)
                is AddItemResult.BlockedByLock -> blocked(trimmed, result)
            }
        }
    }

    /**
     * The recurring half. Also name only.
     *
     * It occupies no day until it is concretized — `expandRecurring` skips a
     * definition without a rule, a start time or a duration rather than guessing
     * at one — so a bare recurring note sits in the Sammelliste harmlessly, next
     * to the bare ToDos, and the evening asks all three at once.
     */
    fun addRecurring(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            _feedback.value = when (val result = itemRepository.addQuickRecurring(trimmed)) {
                is AddItemResult.Added -> QuickAddFeedback.AddedRecurring(trimmed)
                is AddItemResult.BlockedByLock -> blocked(trimmed, result)
            }
        }
    }

    fun dismissFeedback() {
        _feedback.value = null
    }

    private fun blocked(name: String, result: AddItemResult.BlockedByLock) =
        QuickAddFeedback.Blocked(name, result.lockedUntil.toLocalDateTime(timeZone).date)
}
