package com.example.eta.ui.concretize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.ScheduleMaintenance
import com.example.eta.data.repository.SubtaskRepository
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.Subtask
import com.example.eta.ui.attributes.RecurringAttributes
import com.example.eta.ui.attributes.TodoAttributes
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Phase 2 of the daily planning: giving the Quick-Add notes their attributes.
 *
 * Two kinds of note arrive here, and they need different questions. A **ToDo**
 * cannot be planned until it has a category, a priority and a duration — the
 * revolver only offers `isConcretized` cards. A **recurring** note cannot produce
 * a single occurrence until it has weekdays, a start time and a duration, because
 * `expandRecurring` skips a definition that lacks any of them rather than
 * guessing. Either way this step is what turns a jotted line into something the
 * schedule can hold.
 */
class ConcretizeViewModel(
    private val itemRepository: ItemRepository,
    private val scheduleMaintenance: ScheduleMaintenance,
    subtaskRepository: SubtaskRepository,
    private val clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val today: LocalDate = clock.todayIn(timeZone)

    val pending: StateFlow<List<Item>> = itemRepository.observeStage(Stage.COLLECTION)
        .map { items -> items.filterNot { it.isConcretized } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** The steps already inside each card, so the builder opens on what is there. */
    val subtasks: StateFlow<Map<String, List<Subtask>>> = subtaskRepository.observeByItem()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    /** ToDos a group can swallow as steps. */
    val foldable: StateFlow<List<Item>> = itemRepository.observeFoldCandidates()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** The standing schedule, so a recurring note is warned about what it would collide with. */
    val definitions: StateFlow<List<Item>> = itemRepository.observeRecurringDefinitions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** Turns a jotted line into a plannable ToDo. */
    fun concretize(item: Item, name: String, attributes: TodoAttributes) {
        viewModelScope.launch {
            itemRepository.concretize(
                item = renamedIfNeeded(item, name),
                category = attributes.category,
                priority = attributes.priority,
                targetDate = today.plus(DatePeriod(days = attributes.inDays.coerceAtLeast(0))),
                estimatedDuration = attributes.duration,
                travelBefore = attributes.travelBefore,
                returnAfter = attributes.returnAfter,
                breakAfter = attributes.breakAfter,
                endSound = attributes.endSound,
                extras = attributes.extras,
                subtasks = attributes.subtasks,
                folded = attributes.foldedItemIds,
            )
        }
    }

    private fun renamedIfNeeded(item: Item, name: String): Item =
        if (name.isNotBlank() && name != item.name) item.renamed(name, clock.now()) else item

    /**
     * Turns a jotted line into a standing recurring task.
     *
     * Several weekdays become several definitions, which is the questionnaire's
     * own answer to the same problem. The expansion runs straight afterwards, or
     * the task would exist without a single occurrence until some phase happened
     * to lay one down — and the user is looking at the screen that just said it
     * was done.
     */
    fun concretizeRecurring(item: Item, name: String, attributes: RecurringAttributes) {
        if (attributes.weekdays.isEmpty()) return
        viewModelScope.launch {
            itemRepository.concretizeRecurring(
                item = renamedIfNeeded(item, name),
                category = attributes.category,
                weekdays = attributes.weekdays,
                startTime = attributes.startTime,
                duration = attributes.duration,
                travelBefore = attributes.travelBefore,
                returnAfter = attributes.returnAfter,
                breakAfter = attributes.breakAfter,
                endSound = attributes.endSound,
                extras = attributes.extras,
                growth = attributes.growth,
                subtasks = attributes.subtasks,
                folded = attributes.foldedItemIds,
            )
            scheduleMaintenance.topUp()
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
