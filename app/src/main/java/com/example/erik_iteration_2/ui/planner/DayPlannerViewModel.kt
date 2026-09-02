package com.example.erik_iteration_2.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.data.repository.AddItemResult
import com.example.erik_iteration_2.data.repository.ItemRepository
import com.example.erik_iteration_2.data.repository.PlanRepository
import com.example.erik_iteration_2.data.repository.ScheduleMaintenance
import com.example.erik_iteration_2.data.repository.SetupRepository
import com.example.erik_iteration_2.domain.model.BlockOrigin
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.ItemRole
import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.model.Priority
import com.example.erik_iteration_2.domain.model.Stage
import com.example.erik_iteration_2.domain.planning.MINUTES_PER_DAY
import com.example.erik_iteration_2.domain.planning.canPlace
import com.example.erik_iteration_2.domain.planning.firstFreeStart
import com.example.erik_iteration_2.domain.planning.minuteToLocalTime
import com.example.erik_iteration_2.domain.planning.priorityTier
import com.example.erik_iteration_2.domain.planning.sleepStretches
import com.example.erik_iteration_2.domain.planning.snapToGrid
import com.example.erik_iteration_2.domain.reward.CUSTOM_EARN_POINTS_PER_HOUR
import com.example.erik_iteration_2.domain.reward.CUSTOM_SPEND_POINTS_PER_HOUR
import com.example.erik_iteration_2.domain.reward.SOCIAL_POINTS_PER_HOUR
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** What the second revolver offers. Fixed, and not backed by the database. */
enum class SpendKind(val label: String, val pointsPerHour: Double) {
    CUSTOM_EARN("Custom Earn", CUSTOM_EARN_POINTS_PER_HOUR),
    CUSTOM_SPEND("Custom Spend", CUSTOM_SPEND_POINTS_PER_HOUR),
    SOCIAL("Social", SOCIAL_POINTS_PER_HOUR),
}

/** Everything from the second revolver is an hour long until edited. */
val DEFAULT_SPEND_DURATION: Duration = 1.hours

data class PlannerUiState(
    val date: LocalDate,
    val blocks: List<BlockWithItem> = emptyList(),
    /** The week's ToDos of the highest outstanding priority — what the revolver holds. */
    val revolver: List<Item> = emptyList(),
    val revolverPriority: Priority? = null,
    /** Priorities being held back below the one on offer. */
    val lockedPriorities: Int = 0,
    /** Minutes of the day the night covers; shaded rather than drawn as blocks. */
    val sleep: List<IntRange> = emptyList(),
    val isConfirmed: Boolean = false,
    /** Whether the day holds any free time at all — worth saying when it does not. */
    val hasFreeTime: Boolean = true,
)

/** Told to the user after a drop, when the plan did something other than asked. */
sealed interface PlacementFeedback {
    data class Moved(val name: String, val to: LocalTime) : PlacementFeedback
    data class NoRoom(val name: String) : PlacementFeedback

    /** The Sperrliste refused this name. */
    data class Blocked(val name: String) : PlacementFeedback
}

class DayPlannerViewModel(
    private val itemRepository: ItemRepository,
    private val planRepository: PlanRepository,
    setupRepository: SetupRepository,
    private val scheduleMaintenance: ScheduleMaintenance,
    private val clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /** The planner plans the next day, as `Planungsphase.md` describes it. */
    val date: LocalDate = clock.now().toLocalDateTime(timeZone).date.plus(DatePeriod(days = 1))

    private val _feedback = MutableStateFlow<PlacementFeedback?>(null)
    val feedback: StateFlow<PlacementFeedback?> = _feedback.asStateFlow()

    init {
        // The phase materializes what it is about to plan. Idempotent, so opening
        // the planner twice costs nothing.
        viewModelScope.launch { scheduleMaintenance.topUpUntil(date) }
    }

    /** How many priority tiers the user has waved past by hand. */
    private val skippedTiers = MutableStateFlow(0)

    val uiState: StateFlow<PlannerUiState> = combine(
        planRepository.observeDay(date),
        planRepository.observeDayPlan(date),
        itemRepository.observeStage(Stage.WEEK),
        setupRepository.observe(),
        skippedTiers,
    ) { blocks, dayPlan, weekItems, setup, skipped ->
        val placed = blocks.map { it.item.id }.toSet()
        val candidates = weekItems.filter {
            it.id !in placed && it.isConcretized && it.isAvailableOn(date)
        }
        // Only the highest outstanding priority is offered; the rest waits.
        val tier = priorityTier(candidates, skipped)

        PlannerUiState(
            date = date,
            blocks = blocks,
            revolver = tier.items,
            revolverPriority = tier.priority,
            lockedPriorities = tier.lockedBelow,
            sleep = setup?.sleepStretches().orEmpty(),
            isConfirmed = dayPlan?.isConfirmed == true,
            hasFreeTime = blocks.any { it.item.role == ItemRole.FREE_TIME },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlannerUiState(date = date),
    )

    /**
     * Drops a ToDo onto the day.
     *
     * A drop onto occupied time is neither refused nor allowed to overwrite: the
     * block slides to the next place it fits and the user is told where it went.
     * Refusing would make dropping onto a dense day almost impossible, and
     * overwriting would quietly destroy something already planned.
     */
    fun place(item: Item, atMinute: Int) {
        val duration = item.estimatedDuration ?: return
        viewModelScope.launch {
            val existing = uiState.value.blocks.map { it.block }
            val wanted = snapToGrid(atMinute).coerceIn(0, MINUTES_PER_DAY - 1)
            val start = firstFreeStart(existing, duration, wanted)

            if (start == null) {
                _feedback.value = PlacementFeedback.NoRoom(item.name)
                return@launch
            }
            if (start != wanted) {
                _feedback.value = PlacementFeedback.Moved(item.name, minuteToLocalTime(start))
            }

            val now = clock.now()
            planRepository.addBlock(
                PlannedBlock(
                    itemId = item.id,
                    date = date,
                    start = minuteToLocalTime(start),
                    plannedDuration = duration,
                    origin = BlockOrigin.DRAGGED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            // Planning it is what takes it out of the week list.
            itemRepository.moveTo(item, Stage.DAY)
        }
    }

    /** Moving a block already on the day; recurring and imported ones stay put. */
    fun move(block: PlannedBlock, toMinute: Int) {
        if (!block.isMovable) return
        viewModelScope.launch {
            val existing = uiState.value.blocks.map { it.block }
            val wanted = snapToGrid(toMinute).coerceIn(0, MINUTES_PER_DAY - 1)
            val moved = block.copy(start = minuteToLocalTime(wanted), updatedAt = clock.now())

            if (canPlace(existing, moved)) {
                planRepository.addBlock(moved)
                return@launch
            }
            val start = firstFreeStart(existing, block.effectiveDuration, wanted, ignoreId = block.id)
            if (start == null) {
                _feedback.value = PlacementFeedback.NoRoom("Der Block")
                return@launch
            }
            planRepository.addBlock(moved.copy(start = minuteToLocalTime(start)))
        }
    }

    /**
     * Takes a block off the day. A dragged ToDo goes back to the week list so it
     * can be planned again; anything else is simply removed from this day.
     */
    fun remove(entry: BlockWithItem) {
        viewModelScope.launch {
            planRepository.removeBlock(entry.block)
            if (entry.block.origin == BlockOrigin.DRAGGED && entry.item.stage == Stage.DAY) {
                itemRepository.moveTo(entry.item, Stage.WEEK)
            }
        }
    }

    /**
     * Long-press editing: everything a card carries that this screen can change.
     *
     * Name, category and the standing note belong to the **item** and so change
     * every future occurrence; start, duration and the day's note belong to this
     * **block** alone. The dialog says which is which rather than hiding it.
     */
    fun edit(
        entry: BlockWithItem,
        name: String,
        category: Category?,
        start: LocalTime,
        duration: Duration,
        blockNote: String,
        itemNote: String,
    ) {
        viewModelScope.launch {
            val now = clock.now()
            val newItemNote = itemNote.ifBlank { null }
            if (name != entry.item.name ||
                category != entry.item.category ||
                newItemNote != entry.item.note
            ) {
                itemRepository.update(
                    entry.item.renamed(name, now).copy(category = category, note = newItemNote),
                )
            }
            planRepository.addBlock(
                entry.block.copy(
                    start = start,
                    plannedDuration = duration,
                    actualDuration = null,
                    note = blockNote.ifBlank { null },
                    updatedAt = now,
                ),
            )
        }
    }

    /**
     * A ToDo thought of on the spot and dropped straight onto a slot.
     *
     * The ordinary route — Quick-Add, weekly planning, concretizing — is right for
     * an idea that needs a week to settle, and wrong for one that belongs at
     * half past four tomorrow. This creates it already concretized and already on
     * the day, so a spontaneous thought does not have to wait for a phase.
     */
    fun createTodo(name: String, category: Category, duration: Duration, atMinute: Int) {
        viewModelScope.launch {
            val existing = uiState.value.blocks.map { it.block }
            val wanted = snapToGrid(atMinute).coerceIn(0, MINUTES_PER_DAY - 1)
            val start = firstFreeStart(existing, duration, wanted)
            if (start == null) {
                _feedback.value = PlacementFeedback.NoRoom(name)
                return@launch
            }
            if (start != wanted) {
                _feedback.value = PlacementFeedback.Moved(name, minuteToLocalTime(start))
            }

            val now = clock.now()
            val item = Item.newTodo(
                name = name,
                category = category,
                // Planned by hand for a specific slot: it must happen then.
                priority = Priority.URGENT_MUST,
                targetDate = date,
                estimatedDuration = duration,
                now = now,
            ).copy(stage = Stage.DAY)

            if (itemRepository.add(item) is AddItemResult.BlockedByLock) {
                _feedback.value = PlacementFeedback.Blocked(name)
                return@launch
            }
            planRepository.addBlock(
                PlannedBlock(
                    itemId = item.id,
                    date = date,
                    start = minuteToLocalTime(start),
                    plannedDuration = duration,
                    origin = BlockOrigin.DRAGGED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** The second revolver: a named one-off that earns, spends or is simply social. */
    fun createSpend(kind: SpendKind, name: String, pointsPerHour: Double, atMinute: Int) {
        viewModelScope.launch {
            val existing = uiState.value.blocks.map { it.block }
            val wanted = snapToGrid(atMinute).coerceIn(0, MINUTES_PER_DAY - 1)
            val start = firstFreeStart(existing, DEFAULT_SPEND_DURATION, wanted)
            if (start == null) {
                _feedback.value = PlacementFeedback.NoRoom(name)
                return@launch
            }
            if (start != wanted) {
                _feedback.value = PlacementFeedback.Moved(name, minuteToLocalTime(start))
            }

            val now = clock.now()
            val item = Item.newSpend(
                name = name.ifBlank { kind.label },
                pointsPerHour = pointsPerHour,
                now = now,
            )
            // The Sperrliste can veto this name. Ignoring the refusal used to leave
            // a block pointing at an item that was never written — a foreign key
            // violation, and a crash rather than a message.
            if (itemRepository.add(item) is AddItemResult.BlockedByLock) {
                _feedback.value = PlacementFeedback.Blocked(item.name)
                return@launch
            }
            planRepository.addBlock(
                PlannedBlock(
                    itemId = item.id,
                    date = date,
                    start = minuteToLocalTime(start),
                    plannedDuration = DEFAULT_SPEND_DURATION,
                    origin = BlockOrigin.DRAGGED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** Ends the planning phase; the day then reads as the "Liste für Morgen". */
    fun confirm() {
        viewModelScope.launch { planRepository.confirmDayPlan(date) }
    }

    /** The long press on the confirmed list, which brings the revolver back out. */
    fun reopen() {
        viewModelScope.launch { planRepository.reopenDayPlan(date) }
    }

    /**
     * Waves the offered priority past to reach the one below.
     *
     * The escape hatch for a tier that cannot be placed — nothing left in the day
     * fits it — which would otherwise hold the revolver shut for good.
     */
    fun skipPriority() {
        skippedTiers.update { it + 1 }
    }

    /** Back to offering the most important thing again. */
    fun resetPriority() {
        skippedTiers.value = 0
    }

    fun dismissFeedback() {
        _feedback.value = null
    }
}
