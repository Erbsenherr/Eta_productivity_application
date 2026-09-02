package com.example.erik_iteration_2.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.data.repository.ItemRepository
import com.example.erik_iteration_2.data.repository.PlanRepository
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Stage
import com.example.erik_iteration_2.domain.staging.daysUntilBan
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
 * The "Smart toDos" tab: the six lists, side by side with nothing to do.
 *
 * Deliberately read-only. Every one of these lists is the *result* of a phase —
 * the Sperrliste of the weekly sweep, the Wochenliste of the weekly planning, the
 * Tagesliste of the day planner — so editing here would mean a second way to move
 * a card, one that skips the rules the phases enforce.
 */
class SmartListsViewModel(
    itemRepository: ItemRepository,
    planRepository: PlanRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val today: LocalDate = clock.now().toLocalDateTime(timeZone).date
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

    private data class DayLists(
        val todayBlocks: List<BlockWithItem>,
        val tomorrowBlocks: List<BlockWithItem>,
        val confirmed: Boolean,
        val done: List<DoneEntry>,
    )
}
