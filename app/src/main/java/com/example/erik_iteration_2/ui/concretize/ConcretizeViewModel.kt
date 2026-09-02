package com.example.erik_iteration_2.ui.concretize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erik_iteration_2.data.repository.ItemRepository
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Priority
import com.example.erik_iteration_2.domain.model.Stage
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Phase 2 of the daily planning: giving the Quick-Add notes their attributes.
 *
 * Until a ToDo is concretized it cannot be planned — the revolver only offers
 * `isConcretized` cards — so this step is what turns a jotted line into something
 * the week can actually hold.
 */
class ConcretizeViewModel(
    private val itemRepository: ItemRepository,
    private val clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val today: LocalDate = clock.now().toLocalDateTime(timeZone).date

    val pending: StateFlow<List<Item>> = itemRepository.observeStage(Stage.COLLECTION)
        .map { items -> items.filterNot { it.isConcretized } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Turns a jotted line into a plannable ToDo. */
    fun concretize(
        item: Item,
        name: String,
        category: Category,
        priority: Priority,
        inDays: Int,
        duration: Duration,
    ) {
        viewModelScope.launch {
            val renamed = if (name.isNotBlank() && name != item.name) {
                item.renamed(name, clock.now())
            } else {
                item
            }
            itemRepository.concretize(
                item = renamed,
                category = category,
                priority = priority,
                targetDate = today.plus(DatePeriod(days = inDays.coerceAtLeast(0))),
                estimatedDuration = duration,
            )
        }
    }

    /**
     * Throwing one away.
     *
     * Deleting rather than banning: the Sperrliste is for ToDos that were carried
     * around for a month, not for ones dismissed the same evening they were noted.
     */
    fun discard(item: Item) {
        viewModelScope.launch { itemRepository.delete(item) }
    }
}
