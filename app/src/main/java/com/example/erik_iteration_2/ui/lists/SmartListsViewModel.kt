package com.example.erik_iteration_2.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.data.repository.ItemRepository
import com.example.erik_iteration_2.data.repository.PlanRepository
import com.example.erik_iteration_2.data.repository.ScheduleMaintenance
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Stage
import com.example.erik_iteration_2.domain.staging.daysUntilBan
import com.example.erik_iteration_2.ui.attributes.RecurringAttributes
import com.example.erik_iteration_2.ui.attributes.TodoAttributes
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** A Sammelliste entry with how long it has left before the ban. */
data class CollectionEntry(
    val item: Item,
    val daysLeft: Int?,
)

/** A banned ToDo and the day it may be written down again. */
data class LockedEntry(
    val item: Item,
    val until: LocalDate?,
)

/** One line of the Erfolgsliste: name, date and time, as `Konzept.md` asks. */
data class DoneEntry(
    val name: String,
    val at: LocalDateTime,
)

data class SmartListsUiState(
    val today: LocalDate,
    val tomorrow: LocalDate,
    val locked: List<LockedEntry> = emptyList(),
    val collection: List<CollectionEntry> = emptyList(),
    val week: List<Item> = emptyList(),
    val todayBlocks: List<BlockWithItem> = emptyList(),
    val tomorrowBlocks: List<BlockWithItem> = emptyList(),
    /** The "Liste für Morgen" only exists once tomorrow's plan is confirmed. */
    val tomorrowConfirmed: Boolean = false,
    val done: List<DoneEntry> = emptyList(),
)

/**
 * The "Smart toDos" tab: the six lists, side by side.
 *
 * Read-only about **placement**, which is the part that matters: every one of
 * these lists is the result of a phase — the Sperrliste of the weekly sweep, the
 * Wochenliste of the weekly planning, the Tagesliste of the day planner — so
 * moving a card from here would be a second route around the rules those phases
 * enforce, and there is still no way to do it.
 *
 * The *attributes* of a definition are a different matter. A wrong duration or
 * priority is a typo, and making the user run a whole planning phase to correct
 * one is the tail wagging the dog — so [update] and [delete] exist, and the
 * screen offers them on the Sammelliste and the Wochenliste alone.
 */
class SmartListsViewModel(
    private val itemRepository: ItemRepository,
    planRepository: PlanRepository,
    private val scheduleMaintenance: ScheduleMaintenance,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    val today: LocalDate = clock.now().toLocalDateTime(timeZone).date
    private val tomorrow: LocalDate = today.plus(DatePeriod(days = 1))

    private val stages = combine(
        itemRepository.observeStage(Stage.LOCKED),
        itemRepository.observeStage(Stage.COLLECTION),
        itemRepository.observeStage(Stage.WEEK),
    ) { locked, collection, week ->
        Triple(
            locked.map { LockedEntry(it, it.lockedUntil?.toLocalDateTime(timeZone)?.date) },
            collection.map { CollectionEntry(it, it.daysUntilBan(clock.now(), timeZone)) },
            week,
        )
    }

    private val days = combine(
        planRepository.observeDay(today),
        planRepository.observeDay(tomorrow),
        planRepository.observeDayPlan(tomorrow),
        planRepository.observeErfolgsliste(),
    ) { todayBlocks, tomorrowBlocks, dayPlan, done ->
        DayLists(
            todayBlocks = todayBlocks,
            tomorrowBlocks = tomorrowBlocks,
            confirmed = dayPlan?.isConfirmed == true,
            done = done.mapNotNull { entry ->
                entry.block.completedAt?.let {
                    DoneEntry(entry.item.name, it.toLocalDateTime(timeZone))
                }
            },
        )
    }

    val uiState: StateFlow<SmartListsUiState> = combine(stages, days) { staged, dayLists ->
        SmartListsUiState(
            today = today,
            tomorrow = tomorrow,
            locked = staged.first,
            collection = staged.second,
            week = staged.third,
            todayBlocks = dayLists.todayBlocks,
            tomorrowBlocks = dayLists.tomorrowBlocks,
            tomorrowConfirmed = dayLists.confirmed,
            done = dayLists.done,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SmartListsUiState(today = today, tomorrow = tomorrow),
    )

    /**
     * Saves a ToDo's attributes.
     *
     * Nothing here touches the stage or any block — but it does finish the card:
     * the shared form cannot express "no category", so an entry that was
     * `unvollständig` before is `isConcretized` after.
     */
    fun saveTodo(item: Item, name: String, note: String?, attributes: TodoAttributes) {
        viewModelScope.launch {
            val now = clock.now()
            val renamed = if (name != item.name) item.renamed(name, now) else item
            // The evening step's own call, so a card finished here is finished by
            // the same rules — and comes out `isConcretized` either way.
            itemRepository.concretize(
                item = renamed.copy(note = note),
                category = attributes.category,
                priority = attributes.priority,
                targetDate = today.plus(DatePeriod(days = attributes.inDays.coerceAtLeast(0))),
                estimatedDuration = attributes.duration,
                travelBefore = attributes.travelBefore,
                breakAfter = attributes.breakAfter,
                endSound = attributes.endSound,
            )
        }
    }

    /**
     * Finishes a recurring note, through the evening step's own call.
     *
     * The visible consequence is that the entry leaves the Sammelliste: several
     * weekdays become several definitions in `Stage.DAY`, `enteredCollectionAt` is
     * cleared — the one-month clock has nothing left to measure — and the
     * occurrences are laid down at once, or the task would exist without a single
     * one while the user is looking at the screen that just said it was done.
     *
     * Nothing needs clearing up first: a definition without a rule lays down no
     * block, so a card reaching this point has no occurrences to go stale.
     */
    fun saveRecurring(item: Item, name: String, note: String?, attributes: RecurringAttributes) {
        if (attributes.weekdays.isEmpty()) return
        viewModelScope.launch {
            val now = clock.now()
            val renamed = if (name != item.name) item.renamed(name, now) else item
            itemRepository.concretizeRecurring(
                item = renamed.copy(note = note),
                category = attributes.category,
                weekdays = attributes.weekdays,
                startTime = attributes.startTime,
                duration = attributes.duration,
                travelBefore = attributes.travelBefore,
                breakAfter = attributes.breakAfter,
                endSound = attributes.endSound,
            )
            scheduleMaintenance.topUp()
        }
    }

    /**
     * Throws a definition away for good.
     *
     * `planned_blocks` cascades on delete, so this takes its occurrences with it —
     * including completed ones, which leave the Erfolgsliste. That is what
     * "löschen" has to mean for a definition, and the dialog asks twice before
     * calling it.
     */
    fun delete(item: Item) {
        viewModelScope.launch { itemRepository.delete(item) }
    }

    private data class DayLists(
        val todayBlocks: List<BlockWithItem>,
        val tomorrowBlocks: List<BlockWithItem>,
        val confirmed: Boolean,
        val done: List<DoneEntry>,
    )
}
