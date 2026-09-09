package com.example.erik_iteration_2.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.data.repository.AddItemResult
import com.example.erik_iteration_2.data.repository.ItemRepository
import com.example.erik_iteration_2.data.repository.PlanRepository
import com.example.erik_iteration_2.data.repository.ScheduleMaintenance
import com.example.erik_iteration_2.data.repository.SetupRepository
import com.example.erik_iteration_2.data.repository.WeekPlanningService
import com.example.erik_iteration_2.domain.model.BlockOrigin
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.ItemRole
import com.example.erik_iteration_2.domain.model.ItemType
import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.model.Priority
import com.example.erik_iteration_2.domain.model.Stage
import com.example.erik_iteration_2.domain.planning.MINUTES_PER_DAY
import com.example.erik_iteration_2.domain.planning.canPlace
import com.example.erik_iteration_2.domain.planning.firstFreeStart
import com.example.erik_iteration_2.domain.planning.fitsSomewhere
import com.example.erik_iteration_2.domain.planning.minuteToLocalTime
import com.example.erik_iteration_2.domain.planning.priorityTier
import com.example.erik_iteration_2.domain.planning.sleepStretches
import com.example.erik_iteration_2.domain.planning.snapToGrid
import com.example.erik_iteration_2.domain.reward.CUSTOM_EARN_POINTS_PER_HOUR
import com.example.erik_iteration_2.domain.reward.CUSTOM_SPEND_POINTS_PER_HOUR
import com.example.erik_iteration_2.domain.reward.SOCIAL_POINTS_PER_HOUR
import com.example.erik_iteration_2.ui.attributes.TodoAttributes
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

/**
 * Which day the planner is on.
 *
 * [TOMORROW] is the planning phase proper. [TODAY] is the same screen turned on
 * the day already running, so an event can be called off, moved or added once it
 * turns out the evening's plan was wrong. The two differ in their date and in
 * what confirmation means — see [PlannerUiState.isToday].
 */
enum class PlannerDay { TODAY, TOMORROW }

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
    /**
     * Whether this is the day being lived rather than the one being planned.
     * A day under way is always open for editing: it was confirmed last night,
     * and honouring that would put a long press in front of every correction.
     */
    val isToday: Boolean = false,
    /** Whether the day holds any free time at all — worth saying when it does not. */
    val hasFreeTime: Boolean = true,
)

/** Told to the user after a drop, when the plan did something other than asked. */
sealed interface PlacementFeedback {
    data class Moved(val name: String, val to: LocalTime) : PlacementFeedback
    data class NoRoom(val name: String) : PlacementFeedback

    /** The Sperrliste refused this name. */
    data class Blocked(val name: String) : PlacementFeedback

    /** A card was copied into the week list to be carried on with. */
    data class CopiedToWeek(val name: String) : PlacementFeedback
}

/**
 * The day has room for the task and its journey, but not for the break the card
 * asks for.
 *
 * Asked rather than decided: a break the user configured is part of what they
 * meant by the task, and silently dropping it would be the app quietly changing
 * the plan. Answering yes places it without the break — the journey is never
 * dropped, being the time the task takes to reach rather than a courtesy after
 * it.
 */
data class BreakPrompt(
    val name: String,
    val pending: PendingPlacement,
)

/**
 * The placement a [BreakPrompt] is holding back, so answering it can re-run
 * exactly the one that was refused rather than an approximation of it.
 *
 * Two shapes because two things reach the day: a finished card out of the
 * revolver, and one invented on the spot for the slot it was dropped on. Both go
 * through the same placement, which is why both can be asked the same question.
 */
sealed interface PendingPlacement {
    data class FromRevolver(val item: Item, val atMinute: Int) : PendingPlacement
    data class Spontaneous(
        val name: String,
        val attributes: TodoAttributes,
        val atMinute: Int,
    ) : PendingPlacement
}

class DayPlannerViewModel(
    private val itemRepository: ItemRepository,
    private val planRepository: PlanRepository,
    setupRepository: SetupRepository,
    private val scheduleMaintenance: ScheduleMaintenance,
    private val weekPlanningService: WeekPlanningService,
    private val day: PlannerDay = PlannerDay.TOMORROW,
    private val clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /**
     * The day on the screen. `Planungsphase.md` plans the next one; the same
     * screen also serves today, which is how a plan can still be changed while
     * the day it describes is running.
     */
    val date: LocalDate = clock.now().toLocalDateTime(timeZone).date
        .let { if (day == PlannerDay.TOMORROW) it.plus(DatePeriod(days = 1)) else it }

    private val _feedback = MutableStateFlow<PlacementFeedback?>(null)
    val feedback: StateFlow<PlacementFeedback?> = _feedback.asStateFlow()

    private val _breakPrompt = MutableStateFlow<BreakPrompt?>(null)
    val breakPrompt: StateFlow<BreakPrompt?> = _breakPrompt.asStateFlow()

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
            // Today is never locked by its confirmation: that confirmation was
            // last night's planning, and this screen exists to correct it.
            isConfirmed = day == PlannerDay.TOMORROW && dayPlan?.isConfirmed == true,
            isToday = day == PlannerDay.TODAY,
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
        viewModelScope.launch { placeWithMargins(item, atMinute, item.breakAfter) }
    }

    /** The answer to [BreakPrompt]: plan it anyway, and the break falls away. */
    fun placeWithoutBreak() {
        val prompt = _breakPrompt.value ?: return
        _breakPrompt.value = null
        viewModelScope.launch {
            when (val pending = prompt.pending) {
                is PendingPlacement.FromRevolver ->
                    placeWithMargins(pending.item, pending.atMinute, breakAfter = null)

                is PendingPlacement.Spontaneous -> createTodoWithMargins(
                    name = pending.name,
                    attributes = pending.attributes.copy(breakAfter = null),
                    atMinute = pending.atMinute,
                )
            }
        }
    }

    fun dismissBreakPrompt() {
        _breakPrompt.value = null
    }

    /**
     * Placing a card together with whatever margins it brings.
     *
     * Three outcomes, in the order the day allows them: it fits as asked; it fits
     * only without the break, and the user is asked; or it does not fit at all,
     * which is the ordinary refusal. [breakAfter] is passed rather than read off
     * the item so that answering the prompt can retry with it gone.
     */
    private suspend fun placeWithMargins(item: Item, atMinute: Int, breakAfter: Duration?) {
        val duration = item.estimatedDuration ?: return
        val existing = uiState.value.blocks.map { it.block }
        val wanted = snapToGrid(atMinute).coerceIn(0, MINUTES_PER_DAY - 1)
        val travel = item.travelBefore

        val start = firstFreeStart(existing, duration, wanted, leadIn = travel, tailOut = breakAfter)

        if (start == null) {
            // The break is the only part that may be given up, so it is the only
            // thing worth asking about.
            if (breakAfter != null &&
                fitsSomewhere(existing, duration, wanted, leadIn = travel, tailOut = null)
            ) {
                _breakPrompt.value = BreakPrompt(
                    name = item.name,
                    pending = PendingPlacement.FromRevolver(item, wanted),
                )
            } else {
                _feedback.value = PlacementFeedback.NoRoom(item.name)
            }
            return
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
                travelBefore = travel,
                breakAfter = breakAfter,
                origin = BlockOrigin.DRAGGED,
                createdAt = now,
                updatedAt = now,
            ),
        )
        // Planning it is what takes it out of the week list.
        itemRepository.moveTo(item, Stage.DAY)
    }

    /**
     * Moving a block already on the day; recurring and imported ones stay put.
     *
     * Takes an **id**, and looks the row up in its own state. A view model handed
     * a row cannot tell how old it is, and the one the day planner used to hand it
     * was captured by a gesture that outlived the composition it came from — so a
     * drag after an edit wrote the pre-edit block straight back, margins and all.
     * See *Step 12*.
     */
    fun move(blockId: String, toMinute: Int) {
        val block = uiState.value.blocks.firstOrNull { it.block.id == blockId }?.block ?: return
        if (!block.isMovable) return
        viewModelScope.launch {
            val existing = uiState.value.blocks.map { it.block }
            val wanted = snapToGrid(toMinute).coerceIn(0, MINUTES_PER_DAY - 1)
            val moved = block.copy(start = minuteToLocalTime(wanted), updatedAt = clock.now())

            if (canPlace(existing, moved)) {
                planRepository.addBlock(moved)
                return@launch
            }
            val start = firstFreeStart(
                existing,
                block.effectiveDuration,
                wanted,
                ignoreId = block.id,
                leadIn = block.travelBefore,
                tailOut = block.breakAfter,
            )
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
     * Calls an occurrence off without taking it out of the schedule.
     *
     * The counterpart to [remove] for everything the user did not drag in.
     * Deleting a recurring occurrence does not stick — expansion runs forward
     * from today and would lay it down again — so the row stays and is marked
     * discarded instead. No catch-up is offered: calling something off in
     * advance is a decision, not the evening's admission that it did not happen.
     */
    fun cancel(entry: BlockWithItem) {
        viewModelScope.launch { planRepository.discard(entry.block) }
    }

    /** Undoing a calling-off. The block goes back to being an open occurrence. */
    fun uncancel(entry: BlockWithItem) {
        viewModelScope.launch { planRepository.undiscard(entry.block) }
    }

    /**
     * Carries a card on into the week list, as a copy.
     *
     * For the case the user named: the time allotted ran out and the task is not
     * finished. The occurrence stays where it is — it happened, and the
     * Erfolgsliste says so — while a fresh ToDo takes over what is left of it.
     *
     * The copy skips the Sperrliste check, for the same reason a "Nachholen von …"
     * does: it follows from work already under way rather than from an idea being
     * hoarded. It has no target date, so it can be planned at once.
     */
    fun copyToWeek(entry: BlockWithItem) {
        viewModelScope.launch {
            val now = clock.now()
            val copy = Item(
                type = ItemType.TODO,
                name = entry.item.name,
                stage = Stage.WEEK,
                category = entry.item.category,
                note = entry.item.note,
                // Something already being worked on has to happen; a card that
                // never carried a priority still gets one, or the revolver's
                // priority gate would offer it last.
                priority = entry.item.priority ?: Priority.MUST,
                estimatedDuration = entry.block.effectiveDuration,
                // The rest of the same work: it still needs the same journey,
                // still earns the same break, still ends the same way.
                travelBefore = entry.block.travelBefore,
                breakAfter = entry.block.breakAfter,
                endSound = entry.item.endSound,
                enteredCollectionAt = now,
                createdAt = now,
                updatedAt = now,
            )
            itemRepository.takeIntoWeek(copy, weekPlanningService.cycleStart())
            _feedback.value = PlacementFeedback.CopiedToWeek(copy.name)
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
        travelBefore: Duration?,
        breakAfter: Duration?,
        endSound: Boolean,
    ) {
        viewModelScope.launch {
            val now = clock.now()
            val newItemNote = itemNote.ifBlank { null }
            if (name != entry.item.name ||
                category != entry.item.category ||
                newItemNote != entry.item.note ||
                endSound != entry.item.endSound
            ) {
                itemRepository.update(
                    entry.item.renamed(name, now).copy(
                        category = category,
                        note = newItemNote,
                        endSound = endSound,
                    ),
                )
            }
            planRepository.addBlock(
                entry.block.copy(
                    start = start,
                    plannedDuration = duration,
                    actualDuration = null,
                    note = blockNote.ifBlank { null },
                    // Per occurrence: the definition keeps its own defaults, so
                    // adding a journey to today does not add one to every Tuesday.
                    travelBefore = travelBefore,
                    breakAfter = breakAfter,
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
    fun createTodo(name: String, attributes: TodoAttributes, atMinute: Int) {
        viewModelScope.launch { createTodoWithMargins(name, attributes, atMinute) }
    }

    /**
     * The spontaneous card, placed by the same rules as one out of the revolver.
     *
     * Including the break prompt: sharing the placement is what makes the two
     * routes onto the day behave alike, rather than the second one quietly
     * ignoring a margin because it was written later.
     */
    private suspend fun createTodoWithMargins(
        name: String,
        attributes: TodoAttributes,
        atMinute: Int,
    ) {
        val existing = uiState.value.blocks.map { it.block }
        val wanted = snapToGrid(atMinute).coerceIn(0, MINUTES_PER_DAY - 1)
        val duration = attributes.duration
        val travel = attributes.travelBefore

        val start = firstFreeStart(
            existing,
            duration,
            wanted,
            leadIn = travel,
            tailOut = attributes.breakAfter,
        )
        if (start == null) {
            if (attributes.breakAfter != null &&
                fitsSomewhere(existing, duration, wanted, leadIn = travel, tailOut = null)
            ) {
                _breakPrompt.value = BreakPrompt(
                    name = name,
                    pending = PendingPlacement.Spontaneous(name, attributes, wanted),
                )
            } else {
                _feedback.value = PlacementFeedback.NoRoom(name)
            }
            return
        }
        if (start != wanted) {
            _feedback.value = PlacementFeedback.Moved(name, minuteToLocalTime(start))
        }

        val now = clock.now()
        val item = Item.newTodo(
            name = name,
            category = attributes.category,
            // Planned by hand for a specific slot: it must happen then. The form
            // does ask for a priority, but dropping it on an hour has already
            // said something stronger than any of the four.
            priority = Priority.URGENT_MUST,
            targetDate = date,
            estimatedDuration = duration,
            now = now,
        ).copy(
            stage = Stage.DAY,
            travelBefore = travel,
            breakAfter = attributes.breakAfter,
            endSound = attributes.endSound,
        )

        if (itemRepository.add(item) is AddItemResult.BlockedByLock) {
            _feedback.value = PlacementFeedback.Blocked(name)
            return
        }
        planRepository.addBlock(
            PlannedBlock(
                itemId = item.id,
                date = date,
                start = minuteToLocalTime(start),
                plannedDuration = duration,
                travelBefore = travel,
                breakAfter = attributes.breakAfter,
                origin = BlockOrigin.DRAGGED,
                createdAt = now,
                updatedAt = now,
            ),
        )
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
