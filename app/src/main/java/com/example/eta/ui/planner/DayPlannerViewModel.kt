package com.example.eta.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.AddItemResult
import com.example.eta.alarm.WakeAlarmCoordinator
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.NightService
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.PointsRepository
import com.example.eta.data.repository.ScheduleMaintenance
import com.example.eta.data.repository.GroupAnswers
import com.example.eta.data.repository.SubtaskGroupService
import com.example.eta.data.repository.SubtaskRepository
import com.example.eta.data.repository.SetupRepository
import com.example.eta.data.repository.WeekPlanningService
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.DayPlan
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.NightOverride
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.stampedWith
import com.example.eta.domain.model.withExtras
import com.example.eta.domain.planning.BreakFindings
import com.example.eta.domain.planning.PlacementSpan
import com.example.eta.domain.planning.PlanRelief
import com.example.eta.domain.planning.collisions
import com.example.eta.domain.planning.fittedSpan
import com.example.eta.domain.planning.planRelief
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.PlannerWarning
import com.example.eta.domain.planning.checkBreaks
import com.example.eta.domain.planning.canPlace
import com.example.eta.domain.planning.coveringMinute
import com.example.eta.domain.planning.firstFreeStart
import com.example.eta.domain.planning.fitsSomewhere
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.planning.priorityTier
import com.example.eta.domain.planning.sleepStretches
import com.example.eta.domain.planning.snapToGrid
import com.example.eta.domain.planning.startMinute
import com.example.eta.domain.planning.claimsItsSlot
import com.example.eta.domain.planning.occupiesTime
import com.example.eta.domain.planning.standing
import com.example.eta.domain.reevaluation.isOneOffBreak
import com.example.eta.domain.reevaluation.returnedToCollection
import com.example.eta.domain.reward.CUSTOM_EARN_POINTS_PER_HOUR
import com.example.eta.domain.reward.CUSTOM_SPEND_POINTS_PER_HOUR
import com.example.eta.domain.reward.plannedYield
import com.example.eta.domain.setup.UserSetup
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.subtasks.SubtaskSetting
import com.example.eta.ui.subtasks.addedDuration
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/**
 * Which day the planner is on.
 *
 * [TOMORROW] is the planning phase proper. [TODAY] is the same screen turned on
 * the day already running, so an event can be called off, moved or added once it
 * turns out the evening's plan was wrong. The two differ in their date and in
 * what confirmation means — see [PlannerUiState.isToday].
 */
enum class PlannerDay { TODAY, TOMORROW }

/**
 * The two points entries a box on the day can become.
 *
 * They were chambers of a second revolver until step 41, with a neutral
 * "Social" beside them; now they are the two answers behind "Custom", and an
 * hour that neither earns nor costs is one of these with its rate set to 0.
 */
enum class SpendKind(val label: String, val pointsPerHour: Double) {
    CUSTOM_EARN("Custom Earn", CUSTOM_EARN_POINTS_PER_HOUR),
    CUSTOM_SPEND("Custom Spend", CUSTOM_SPEND_POINTS_PER_HOUR),
}

data class PlannerUiState(
    val date: LocalDate,
    /** What still stands on the day, and what a placement has to work around. */
    val blocks: List<BlockWithItem> = emptyList(),
    /**
     * Finished blocks: drawn as a green stretch rather than as a block.
     *
     * They have released their slot — something else can be planned into it — and
     * leaving it empty costs nothing, because the settlement still counts the
     * hours a completed block carried. See `claimsItsSlot`.
     */
    val freed: List<BlockWithItem> = emptyList(),
    /** The week's ToDos of the highest outstanding priority — what the revolver holds. */
    val revolver: List<Item> = emptyList(),
    val revolverPriority: Priority? = null,
    /** Priorities being held back below the one on offer. */
    val lockedPriorities: Int = 0,
    /** Higher priorities passed by themselves, nothing of them fitting the day. */
    val passedPriorities: Int = 0,
    /** Whether "Überspringen" is offered — the Flexibilität setting. */
    val canSkipPriority: Boolean = false,
    /** Whether tiers have been waved past by hand, so there is a way back to offer. */
    val prioritySkipped: Boolean = false,
    /**
     * Whether a ToDo may be made on the spot by holding an empty stretch.
     *
     * Not while the week list still holds something that could go on this day
     * instead — unless the Flexibilität setting says otherwise. "Could go" is
     * the same test the priority gate applies: a card that fits nowhere on the
     * day is not one that is being dodged.
     */
    val canCreateTodo: Boolean = true,
    /**
     * The standing answers and the nights changed for one date — what the
     * window behind a long press on the shaded night is drawn from.
     */
    val setup: UserSetup? = null,
    val nightOverrides: Map<LocalDate, NightOverride> = emptyMap(),
    /** What the Pausenchecker found, or null while it is switched off. */
    val breaks: BreakFindings? = null,
    /** The warnings waved away for this day. */
    val dismissedWarnings: Set<PlannerWarning> = emptySet(),
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
    /** What the day is worth once everything on it is done — the forecast. */
    val plannedYield: Double = 0.0,
    /** The steps inside each card that has any, by item id. */
    val subtasks: Map<String, List<Subtask>> = emptyMap(),
    /** Which steps this day has ticked off, by block id. */
    val checked: Map<String, Set<String>> = emptyMap(),
    /** ToDos a group can swallow as steps: the week's goals and the backlog. */
    val foldable: List<Item> = emptyList(),
)

/**
 * The reason stored for a cancellation excused by holding "Absagen" down.
 *
 * The evening's own excuse asks for a sentence; this one is given in the moment
 * and has none, so the record says how it came about instead.
 */
const val FORCE_MAJEURE_ON_THE_SPOT = "beim Absagen angegeben"

/** Told to the user after a drop, when the plan did something other than asked. */
sealed interface PlacementFeedback {
    data class Moved(val name: String, val to: LocalTime) : PlacementFeedback
    data class NoRoom(val name: String) : PlacementFeedback

    /** The Sperrliste refused this name. */
    data class Blocked(val name: String) : PlacementFeedback

    /** A card was copied into the week list to be carried on with. */
    data class CopiedToWeek(val name: String) : PlacementFeedback

    /** A card was dropped back on the revolver and is a week goal again. */
    data class ReturnedToWeek(val name: String) : PlacementFeedback

    /** A break was dropped back on the revolver, and is simply gone. */
    data class Removed(val name: String) : PlacementFeedback

    /** The whole day was called off at once; [count] cards were dealt with. */
    data class DayCleared(val count: Int) : PlacementFeedback

    /**
     * A group was made, but the day had no stretch long enough for it — so it
     * stands where it was and overlaps what follows.
     *
     * Said rather than refused: the merge is about the tasks, and taking the
     * answer back at the last step because the afternoon is full would be worse
     * than an overlap the Überschneidungs-Box on the dashboard reports anyway.
     */
    data class Overlapping(val name: String) : PlacementFeedback
}

/**
 * A movable block was dropped on another one, which is two questions at once:
 * group them, or just move it there as the planner always did.
 *
 * Held as **ids**. The blocks behind them are resolved wherever they are needed —
 * the screen redraws the dialog from the current state and closes it if either has
 * gone, and the write reads both rows itself. See [SubtaskGroupService].
 */
data class MergeRequest(
    val draggedId: String,
    val targetId: String,
    val toMinute: Int,
)

/**
 * Something about to be put on the day, held until it is known where and how.
 *
 * Four shapes because four things reach the day: a finished card out of the
 * revolver, and the three a box held into the day can become. All go through the
 * same placement, which is why all of them can be asked the same question — see
 * [OverlapPrompt].
 */
sealed interface PendingPlacement {
    val name: String

    /** The note for this one occurrence, if the form asked for one. */
    val note: String get() = ""

    data class FromRevolver(val item: Item) : PendingPlacement {
        override val name: String get() = item.name
    }

    data class Todo(
        override val name: String,
        val attributes: TodoAttributes,
        override val note: String,
    ) : PendingPlacement

    data class Spend(
        val kind: SpendKind,
        override val name: String,
        val pointsPerHour: Double,
        override val note: String,
    ) : PendingPlacement

    data class Break(override val note: String) : PendingPlacement {
        override val name: String get() = "Pause"
    }
}

/**
 * A card was put where something already stands.
 *
 * Asked rather than decided. The planner used to slide the card to the next free
 * hour, or refuse it when the day had none — which is the app choosing a time
 * the user did not choose. The three answers: put it there regardless, let the
 * card give way ([fitted], null when nothing of it would be left), or let the
 * plan give way ([relief], one entry per block in its path).
 *
 * [fitted] and [relief] are what the dialog **shows**. Both are worked out again
 * from the day as it then stands when an answer is given.
 */
data class OverlapPrompt(
    val pending: PendingPlacement,
    val wanted: PlacementSpan,
    val fitted: PlacementSpan?,
    val relief: List<PlanRelief>,
)

/** What becomes of a block "Plan anpassen" or "Ganzen Tag absagen" clears away. */
enum class RemovalFate { DELETED, BACK_TO_WEEK, CALLED_OFF }

/**
 * Which of the three a block gets, and it follows from what the block is: a
 * break made for this day is simply gone, a ToDo the user dragged in goes back
 * to the week list, and everything else — the standing schedule, a points entry
 * — is called off, there being no list for it to return to.
 */
fun BlockWithItem.removalFate(): RemovalFate = when {
    block.isHandPlaced && item.isOneOffBreak -> RemovalFate.DELETED
    block.isHandPlaced && item.type == ItemType.TODO -> RemovalFate.BACK_TO_WEEK
    else -> RemovalFate.CALLED_OFF
}

/** The standing answers, and what this day carries beside its blocks. */
private data class DayFrame(
    val setup: UserSetup?,
    val overrides: Map<LocalDate, NightOverride>,
    val dismissed: Set<PlannerWarning>,
)

/** The four things about one day that are always read together. */
private data class DayState(
    val blocks: List<BlockWithItem>,
    val dayPlan: DayPlan?,
    val subtasks: Map<String, List<Subtask>>,
    val checked: Map<String, Set<String>>,
)

class DayPlannerViewModel(
    private val itemRepository: ItemRepository,
    private val planRepository: PlanRepository,
    private val subtaskRepository: SubtaskRepository,
    private val subtaskGroupService: SubtaskGroupService,
    setupRepository: SetupRepository,
    private val scheduleMaintenance: ScheduleMaintenance,
    private val weekPlanningService: WeekPlanningService,
    pointsRepository: PointsRepository,
    private val nightService: NightService,
    private val wakeAlarmCoordinator: WakeAlarmCoordinator,
    private val day: PlannerDay = PlannerDay.TOMORROW,
    private val clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    /**
     * The day on the screen. `Planungsphase.md` plans the next one; the same
     * screen also serves today, which is how a plan can still be changed while
     * the day it describes is running.
     */
    val date: LocalDate = clock.todayIn(timeZone)
        .let { if (day == PlannerDay.TOMORROW) it.plus(DatePeriod(days = 1)) else it }

    /** The real today, for the cycle stamp a released step carries. */
    private val today: LocalDate = clock.todayIn(timeZone)

    private val _feedback = MutableStateFlow<PlacementFeedback?>(null)
    val feedback: StateFlow<PlacementFeedback?> = _feedback.asStateFlow()

    private val _overlapPrompt = MutableStateFlow<OverlapPrompt?>(null)
    val overlapPrompt: StateFlow<OverlapPrompt?> = _overlapPrompt.asStateFlow()

    private val _mergeRequest = MutableStateFlow<MergeRequest?>(null)
    val mergeRequest: StateFlow<MergeRequest?> = _mergeRequest.asStateFlow()

    init {
        // The phase materializes what it is about to plan. Idempotent, so opening
        // the planner twice costs nothing.
        viewModelScope.launch { scheduleMaintenance.topUpUntil(date) }
    }

    /** How many priority tiers the user has waved past by hand. */
    private val skippedTiers = MutableStateFlow(0)

    /**
     * Whether a Custom Spend may be planned: only with points to spend.
     *
     * The account as it stands, not as tonight will leave it — points are
     * credited in the evening, and spending against a harvest that has not
     * happened is how an account goes below zero and stays there. True until the
     * balance has been read, so the card is not shown locked for a frame.
     */
    val canSpend: StateFlow<Boolean> = pointsRepository.observeBalance()
        .map { it > 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** The day, its plan and the steps inside its cards — read together. */
    private val dayState = combine(
        planRepository.observeDay(date),
        planRepository.observeDayPlan(date),
        subtaskRepository.observeByItem(),
        subtaskRepository.observeChecked(date),
    ) { blocks, dayPlan, subtasks, checked -> DayState(blocks, dayPlan, subtasks, checked) }

    private val frame = combine(
        setupRepository.observe(),
        nightService.observeOverrides(),
        nightService.observeDismissed(date),
    ) { setup, overrides, dismissed -> DayFrame(setup, overrides, dismissed) }

    val uiState: StateFlow<PlannerUiState> = combine(
        dayState,
        itemRepository.observeStage(Stage.WEEK),
        frame,
        skippedTiers,
        itemRepository.observeFoldCandidates(),
    ) { read, weekItems, frame, skipped, foldable ->
        val (allBlocks, dayPlan, subtasks, checked) = read
        val setup = frame.setup
        // Two ways a block leaves the plan, and they look different on screen. A
        // **cancellation** gives its hours back and is not drawn at all. A
        // **completed** block has been dealt with: the stretch it held is free
        // again, and the day shades it green rather than leaving a finished task
        // standing in the way of the afternoon.
        val accounted = allBlocks.filter { it.occupiesTime() }
        val blocks = allBlocks.filter { it.claimsItsSlot() }
        val freed = accounted.filter { it.block.isCompleted }
        // The item behind a finished block is still placed: it must not come back
        // out of the revolver because its stretch was released.
        val placed = accounted.map { it.item.id }.toSet()
        val candidates = weekItems.filter {
            it.id !in placed && it.isConcretized && it.isAvailableOn(date)
        }
        // Only the highest outstanding priority is offered; the rest waits — a
        // tier of which nothing fits the day any more is passed by itself, and
        // by hand only where the settings allow that.
        val standing = blocks.standing()
        val fits = { item: Item ->
            val duration = item.estimatedDuration
            duration != null && fitsSomewhere(
                standing,
                duration,
                preferredStart = 0,
                leadIn = item.travelBefore,
                tailOut = item.returnAfter,
            )
        }
        val canSkip = setup?.allowPrioritySkip == true
        val tier = priorityTier(candidates, if (canSkip) skipped else 0, fits)

        PlannerUiState(
            date = date,
            blocks = blocks,
            freed = freed,
            revolver = tier.items,
            revolverPriority = tier.priority,
            lockedPriorities = tier.lockedBelow,
            passedPriorities = tier.passedAbove,
            canSkipPriority = canSkip,
            prioritySkipped = canSkip && skipped > 0,
            canCreateTodo = setup?.allowTodoBesideWeek == true || candidates.none(fits),
            sleep = setup?.sleepStretches(date, frame.overrides).orEmpty(),
            setup = setup,
            nightOverrides = frame.overrides,
            breaks = setup?.takeIf { it.breakChecker }?.let {
                checkBreaks(allBlocks, it.breakPerTwoHours, it.breakMaxStretch)
            },
            dismissedWarnings = frame.dismissed,
            // Today is never locked by its confirmation: that confirmation was
            // last night's planning, and this screen exists to correct it.
            isConfirmed = day == PlannerDay.TOMORROW && dayPlan?.isConfirmed == true,
            isToday = day == PlannerDay.TODAY,
            // Off everything the day accounts for, not off what is still
            // standing: an hour of free time already taken is still free time the
            // day provided for, and saying it has none would be wrong.
            hasFreeTime = accounted.any { it.item.role == ItemRole.FREE_TIME },
            plannedYield = plannedYield(allBlocks),
            subtasks = subtasks,
            checked = checked,
            foldable = foldable,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlannerUiState(date = date),
    )

    /**
     * Drops a ToDo onto the day, at the minute it was dropped on.
     *
     * A drop onto occupied time is a question — see [OverlapPrompt].
     */
    fun place(item: Item, atMinute: Int) {
        val duration = item.estimatedDuration ?: return
        request(
            PendingPlacement.FromRevolver(item),
            spanAt(atMinute, duration, item.travelBefore, item.returnAfter, item.breakAfter),
        )
    }

    private fun spanAt(
        atMinute: Int,
        duration: Duration,
        travelBefore: Duration? = null,
        returnAfter: Duration? = null,
        breakAfter: Duration? = null,
    ) = PlacementSpan(
        startMinute = snapToGrid(atMinute).coerceIn(0, MINUTES_PER_DAY - 1),
        duration = duration,
        travelBefore = travelBefore,
        returnAfter = returnAfter,
        breakAfter = breakAfter,
    )

    /**
     * The one way anything new reaches the day: written at once where it fits,
     * asked about where it does not.
     *
     * Compared as containers, so a card whose break alone runs into the next
     * task is asked about as well — and "ToDo anpassen" then answers by leaving
     * the break out, which is what the planner's own question about a break
     * that does not fit used to be.
     */
    private fun request(pending: PendingPlacement, wanted: PlacementSpan) {
        val existing = uiState.value.blocks.standing()
        if (wanted.collisions(existing).isEmpty()) {
            viewModelScope.launch { commit(pending, wanted) }
            return
        }
        _overlapPrompt.value = OverlapPrompt(
            pending = pending,
            wanted = wanted,
            fitted = fittedSpan(existing, wanted),
            relief = planRelief(existing, wanted),
        )
    }

    /** "Dennoch setzen": where it was put, overlap and all. */
    fun placeAnyway() {
        val prompt = _overlapPrompt.value ?: return
        _overlapPrompt.value = null
        viewModelScope.launch { commit(prompt.pending, prompt.wanted) }
    }

    /**
     * "ToDo anpassen": the card is cut to the stretch that is free.
     *
     * Worked out again rather than taken from the prompt — the day is the one
     * thing here that can have changed while a dialog stood open.
     */
    fun placeFitted() {
        val prompt = _overlapPrompt.value ?: return
        _overlapPrompt.value = null
        val fitted = fittedSpan(uiState.value.blocks.standing(), prompt.wanted)
        if (fitted == null) {
            _feedback.value = PlacementFeedback.NoRoom(prompt.pending.name)
            return
        }
        viewModelScope.launch { commit(prompt.pending, fitted) }
    }

    /** "Plan anpassen": the card stands, and what is in its way is cut or cleared. */
    fun placeAdjustingPlan() {
        val prompt = _overlapPrompt.value ?: return
        _overlapPrompt.value = null
        viewModelScope.launch { commit(prompt.pending, prompt.wanted, adjustPlan = true) }
    }

    fun dismissOverlap() {
        _overlapPrompt.value = null
    }

    /**
     * Writes a placement: the card first, then the room for it, then the block.
     *
     * In that order because the Sperrliste can still refuse a new card, and a
     * plan cut open for something that then never arrives would be the worst of
     * the three outcomes.
     */
    private suspend fun commit(
        pending: PendingPlacement,
        span: PlacementSpan,
        adjustPlan: Boolean = false,
    ) {
        val now = clock.now()
        val item = itemFor(pending, span, now) ?: return
        if (adjustPlan) makeRoomFor(span, now)

        planRepository.addBlock(
            PlannedBlock(
                itemId = item.id,
                date = date,
                start = minuteToLocalTime(span.startMinute),
                plannedDuration = span.duration,
                travelBefore = span.travelBefore,
                returnAfter = span.returnAfter,
                breakAfter = span.breakAfter,
                // On the block: a card made for one slot has one occurrence, and
                // this is the note the edit dialog shows for it.
                note = pending.note.ifBlank { null },
                origin = BlockOrigin.DRAGGED,
                createdAt = now,
                updatedAt = now,
            ).stampedWith(item),
        )
        // Planning it is what takes it out of the week list.
        if (pending is PendingPlacement.FromRevolver) itemRepository.moveTo(item, Stage.DAY)
    }

    /**
     * The card behind a placement — the revolver's own, or one written now.
     *
     * Null when the Sperrliste refused the name, which has been said by then.
     * Ignoring that refusal once left a block pointing at an item that was never
     * written: a foreign key violation, and a crash rather than a message.
     */
    private suspend fun itemFor(pending: PendingPlacement, span: PlacementSpan, now: Instant): Item? =
        when (pending) {
            is PendingPlacement.FromRevolver -> pending.item

            is PendingPlacement.Todo -> {
                val attributes = pending.attributes
                val item = Item.newTodo(
                    name = pending.name,
                    category = attributes.category,
                    // Planned by hand for a specific slot: it must happen then. The
                    // form does ask for a priority, but putting it on an hour has
                    // already said something stronger than any of the four.
                    priority = Priority.URGENT_MUST,
                    targetDate = date,
                    // What the form said, not what "ToDo anpassen" left of it: the
                    // card is that long, this occurrence of it is shorter.
                    estimatedDuration = attributes.duration,
                    now = now,
                ).copy(
                    stage = Stage.DAY,
                    travelBefore = attributes.travelBefore,
                    returnAfter = attributes.returnAfter,
                    breakAfter = attributes.breakAfter,
                    endSound = attributes.endSound,
                ).withExtras(attributes.extras)
                item.takeUnless { refused(itemRepository.add(it, attributes.subtasks, attributes.foldedItemIds), it) }
            }

            is PendingPlacement.Spend -> {
                val item = Item.newSpend(
                    name = pending.name,
                    pointsPerHour = pending.pointsPerHour,
                    now = now,
                )
                item.takeUnless { refused(itemRepository.add(it), it) }
            }

            // The card the dashboard slips in after a finished task, planned
            // ahead: `ItemRole.BREAK` and no category, so it neither earns nor
            // costs. A new one per break — the unique index allows an item one
            // occurrence a day — and the Sperrliste is not consulted: "Pause"
            // is not an idea being hoarded.
            is PendingPlacement.Break -> Item.spontaneousBreak(span.duration, now).also {
                itemRepository.addWithoutLockCheck(it)
            }
        }

    /** Whether the Sperrliste turned [item] away — and says so if it did. */
    private fun refused(result: AddItemResult, item: Item): Boolean {
        if (result !is AddItemResult.BlockedByLock) return false
        _feedback.value = PlacementFeedback.Blocked(item.name)
        return true
    }

    /** Cuts or clears what stands in [span]'s way — "Plan anpassen". */
    private suspend fun makeRoomFor(span: PlacementSpan, now: Instant) {
        val standing = uiState.value.blocks.filter { it.claimsItsSlot() }
        planRelief(standing.map { it.block }, span).forEach { relief ->
            when (relief) {
                is PlanRelief.EndsEarlier -> planRepository.addBlock(relief.result.copy(updatedAt = now))
                is PlanRelief.StartsLater -> planRepository.addBlock(relief.result.copy(updatedAt = now))
                is PlanRelief.Removed ->
                    standing.firstOrNull { it.block.id == relief.block.id }?.let { clearFromDay(it) }

                is PlanRelief.Kept -> Unit
            }
        }
    }

    /** Takes one block off the day by the route its kind has — see [removalFate]. */
    private suspend fun clearFromDay(entry: BlockWithItem) {
        when (entry.removalFate()) {
            RemovalFate.DELETED -> {
                planRepository.removeBlock(entry.block)
                itemRepository.delete(entry.item)
            }

            RemovalFate.BACK_TO_WEEK -> {
                planRepository.removeBlock(entry.block)
                if (entry.item.stage == Stage.DAY) itemRepository.moveTo(entry.item, Stage.WEEK)
            }

            RemovalFate.CALLED_OFF -> planRepository.discard(entry.block)
        }
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
        val state = uiState.value
        val entry = state.blocks.firstOrNull { it.block.id == blockId } ?: return
        if (!entry.block.isMovable) return

        // Dropped on top of another movable task? Then this is a question, not a
        // slide: the two may be meant as one activity. The old behaviour is one of
        // the answers, because dropping onto occupied time in order to get to the
        // next free slot is a gesture the planner has always had and a dense day
        // needs.
        //
        // Only movable targets. A recurring occurrence and an imported appointment
        // cannot be dragged, so they can be neither grouped nor grouped into —
        // which is what keeps a merge from being able to swallow a standing task.
        val landedOn = state.blocks
            .filterNot { it.block.id == blockId }
            .filter { it.block.isHandPlaced }
            .coveringMinute(toMinute.coerceIn(0, MINUTES_PER_DAY - 1))
            .firstOrNull()
        // Hand-placed on both sides: a flexible occurrence of a standing task
        // can be dragged, but it is still that task and is not to be grouped.
        if (landedOn != null && entry.block.isHandPlaced) {
            _mergeRequest.value = MergeRequest(
                draggedId = blockId,
                targetId = landedOn.block.id,
                toMinute = toMinute,
            )
            return
        }
        moveAnyway(blockId, toMinute)
    }

    /** The slide the planner has always done, and the "nur verschieben" answer. */
    fun moveAnyway(blockId: String, toMinute: Int) {
        _mergeRequest.value = null
        val block = uiState.value.blocks.firstOrNull { it.block.id == blockId }?.block ?: return
        if (!block.isMovable) return
        viewModelScope.launch {
            val existing = uiState.value.blocks.standing()
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
                tailOut = block.returnAfter,
                tailOutExtra = block.breakAfter,
            )
            if (start == null) {
                _feedback.value = PlacementFeedback.NoRoom("Der Block")
                return@launch
            }
            planRepository.addBlock(moved.copy(start = minuteToLocalTime(start)))
        }
    }

    /**
     * Saves a group's steps from the block dialog, and everything that decision
     * dragged along with it.
     *
     * Written here rather than handed back to a form: the card exists already, and
     * the builder's own Sichern is the commit point. Three consequences travel with
     * the list — a rename, the tasks it swallowed, and the steps taken back out:
     *
     * - **Folding adds time.** The swallowed task's minutes go to this occurrence
     *   *and* to the definition, so the next one is as long as this one. The block
     *   is not slid to make room: the user is editing what stands here, and the
     *   Überschneidungs-Box says so if it now runs into the afternoon.
     * - **A released step becomes an unfinished card** in the week list — name and
     *   note are all it ever had. The Sperrliste is not consulted, for the reason a
     *   make-up is not: it follows from work already under way.
     */
    fun saveSubtasks(blockId: String, setting: SubtaskSetting) {
        viewModelScope.launch {
            val block = planRepository.findBlock(blockId) ?: return@launch
            val item = itemRepository.findById(block.itemId) ?: return@launch
            val now = clock.now()
            val added = setting.folded.addedDuration()

            subtaskRepository.save(item.id, setting.subtasks)

            val renamed = if (setting.groupName.isNotBlank() && setting.groupName != item.name) {
                item.renamed(setting.groupName, now)
            } else {
                item
            }
            itemRepository.update(
                renamed.copy(
                    estimatedDuration = (item.estimatedDuration ?: block.effectiveDuration) + added,
                ),
            )
            if (added > Duration.ZERO) {
                planRepository.addBlock(
                    block.copy(plannedDuration = block.plannedDuration + added, updatedAt = now),
                )
            }
            itemRepository.retireFolded(setting.folded.map { it.itemId })
            setting.released.forEach { draft ->
                itemRepository.addWithoutLockCheck(
                    Item.releasedSubtask(
                        name = draft.name,
                        note = draft.note,
                        today = today,
                        now = now,
                    ),
                )
            }
        }
    }

    fun dismissMerge() {
        _mergeRequest.value = null
    }

    /**
     * Folds one of the two blocks into the other, wherever the question came from.
     *
     * The group starts where the **target** block started — the drop names a time,
     * and that block is the time it named. It is longer than either part was, so it
     * may no longer fit there: then it slides to the next stretch that holds it,
     * exactly as a drop does. If the day has no such stretch it stays put and
     * overlaps, and the user is told — see [PlacementFeedback.Overlapping].
     */
    fun fold(survivorId: String, dissolvedId: String, answers: GroupAnswers) {
        val request = _mergeRequest.value
        _mergeRequest.value = null
        val state = uiState.value
        val anchorId = request?.targetId ?: survivorId
        val wanted = state.blocks.firstOrNull { it.block.id == anchorId }?.block?.startMinute()
            ?: return

        viewModelScope.launch {
            val obstacles = state.blocks
                .filterNot { it.block.id == survivorId || it.block.id == dissolvedId }
                .standing()
            val start = firstFreeStart(
                obstacles,
                answers.duration,
                wanted,
                leadIn = answers.travelBefore,
                tailOut = answers.returnAfter,
                tailOutExtra = answers.breakAfter,
            )
            val placed = start ?: wanted
            if (!subtaskGroupService.fold(survivorId, dissolvedId, answers, placed)) return@launch

            _feedback.value = when {
                start == null -> PlacementFeedback.Overlapping(answers.name)
                placed != wanted -> PlacementFeedback.Moved(answers.name, minuteToLocalTime(placed))
                else -> null
            }
        }
    }

    /**
     * Takes a block off the day. A dragged ToDo goes back to the week list so it
     * can be planned again.
     *
     * The dialog's button and a drop onto the revolver are the same act, so they
     * are the same call — see [returnToWeek].
     */
    fun remove(entry: BlockWithItem) = returnToWeek(entry.block.id)

    /**
     * Calls an occurrence off without taking it out of the schedule.
     *
     * Offered for **every** block, not only the ones that cannot be handed back:
     * calling a ToDo off and taking it off the day mean different things, and only
     * one of them costs. Deleting a recurring occurrence would not stick anyway —
     * expansion runs forward from today and would lay it down again — so the row
     * stays and is marked discarded, which is also how the evening knows to charge
     * for it. No catch-up is offered: calling something off in advance is a
     * decision, not the evening's admission that it did not happen.
     */
    fun cancel(entry: BlockWithItem, forceMajeure: Boolean = false) {
        viewModelScope.launch {
            // Held down rather than tapped: called off, and excused in the same
            // breath. The evening then has no charge to book for it.
            planRepository.discard(
                entry.block,
                forceMajeure = FORCE_MAJEURE_ON_THE_SPOT.takeIf { forceMajeure },
            )
            // The same consequence the evening's "Fällt aus" has, and for the same
            // reason: a called-off ToDo would otherwise sit in `Stage.DAY` with no
            // block to show it, invisible in every list. `returnedToCollection`
            // rather than `moveTo`, because the one-month clock must not restart —
            // a ToDo planned and dropped over and over is what the Sperrliste is
            // for.
            // Not a break, though: that one stays the day's own and is on no list.
            if (entry.item.type == ItemType.TODO && entry.item.stage == Stage.DAY &&
                !entry.item.isOneOffBreak
            ) {
                itemRepository.update(entry.item.returnedToCollection(clock.now()))
            }
        }
    }

    /**
     * Hands a planned ToDo back to the week list.
     *
     * What a drop onto the revolver means, and what "Vom Tag nehmen" always meant:
     * the day gives the hours up, the task does not. Free, unlike [cancel] — the
     * task is still going to happen, just not here.
     */
    fun returnToWeek(blockId: String) {
        val entry = uiState.value.blocks.firstOrNull { it.block.id == blockId } ?: return
        if (!entry.block.isHandPlaced) return
        viewModelScope.launch {
            planRepository.removeBlock(entry.block)
            // A break has no week list to go back to: it was made for this
            // day, so taking it off the day takes it away.
            if (entry.item.isOneOffBreak) {
                itemRepository.delete(entry.item)
                _feedback.value = PlacementFeedback.Removed(entry.item.name)
                return@launch
            }
            if (entry.item.stage == Stage.DAY) itemRepository.moveTo(entry.item, Stage.WEEK)
            _feedback.value = PlacementFeedback.ReturnedToWeek(entry.item.name)
        }
    }

    /**
     * Calls the whole day off in one gesture.
     *
     * One button, two different acts, and which one a card gets follows from what
     * the card is rather than from a second question to the user: a ToDo that was
     * dragged onto the day goes **back to the week list**, where it can be planned
     * on another day; everything else — the standing schedule, a points entry, an
     * imported appointment — is **called off**, because there is no list for it to
     * return to and deleting it would not stick anyway.
     *
     * Only open blocks. What is already done happened, and what is already called
     * off has been decided; asking either of them again would only be a way to
     * rewrite the day's record.
     */
    fun cancelWholeDay() {
        viewModelScope.launch {
            val entries = uiState.value.blocks.filter { it.block.isOpen }
            entries.forEach { clearFromDay(it) }
            _feedback.value = PlacementFeedback.DayCleared(entries.size)
        }
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
                returnAfter = entry.block.returnAfter,
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
     *
     * [pointsPerHour] is the rate of a points entry from the second revolver, and
     * lives on the item as well — such an item has exactly the one block, so there
     * is no other occurrence it could leak into.
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
        returnAfter: Duration?,
        breakAfter: Duration?,
        endSound: Boolean,
        pointsPerHour: Double?,
        flexible: Boolean = entry.block.flexible,
    ) {
        viewModelScope.launch {
            val now = clock.now()
            val newItemNote = itemNote.ifBlank { null }
            if (name != entry.item.name ||
                category != entry.item.category ||
                newItemNote != entry.item.note ||
                endSound != entry.item.endSound ||
                pointsPerHour != entry.item.pointsPerHour
            ) {
                itemRepository.update(
                    entry.item.renamed(name, now).copy(
                        category = category,
                        note = newItemNote,
                        endSound = endSound,
                        pointsPerHour = pointsPerHour,
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
                    returnAfter = returnAfter,
                    breakAfter = breakAfter,
                    // Only a standing occurrence can be made flexible; anything
                    // else either moves already or is the calendar's.
                    flexible = flexible && entry.block.origin == BlockOrigin.RECURRING,
                    updatedAt = now,
                ),
            )
        }
    }

    /**
     * A ToDo thought of on the spot, for the box that was held into the day.
     *
     * The ordinary route — Quick-Add, weekly planning, concretizing — is right for
     * an idea that needs a week to settle, and wrong for one that belongs at
     * half past four tomorrow. This creates it already concretized and already on
     * the day, so a spontaneous thought does not have to wait for a phase.
     */
    fun createTodo(name: String, attributes: TodoAttributes, note: String, atMinute: Int) {
        request(
            PendingPlacement.Todo(name, attributes, note),
            spanAt(
                atMinute,
                attributes.duration,
                attributes.travelBefore,
                attributes.returnAfter,
                attributes.breakAfter,
            ),
        )
    }

    /** "Custom": a named one-off that earns or spends, as long as its box. */
    fun createSpend(
        kind: SpendKind,
        name: String,
        pointsPerHour: Double,
        note: String,
        atMinute: Int,
        duration: Duration,
    ) {
        request(
            PendingPlacement.Spend(kind, name.ifBlank { kind.label }, pointsPerHour, note),
            spanAt(atMinute, duration),
        )
    }

    /** "Pause": a break as long as its box, placed like any card and worth nothing. */
    fun createBreak(note: String, atMinute: Int, duration: Duration) {
        request(PendingPlacement.Break(note), spanAt(atMinute, duration))
    }

    /**
     * The night that ends on [wakeDate] gets hours of its own — that night and
     * no other. See [NightService.overrideNight] for what moves with it.
     */
    fun changeNight(wakeDate: LocalDate, sleep: LocalTime, wake: LocalTime) {
        viewModelScope.launch {
            nightService.overrideNight(wakeDate, sleep, wake)
            wakeAlarmCoordinator.reschedule()
        }
    }

    fun resetNight(wakeDate: LocalDate) {
        viewModelScope.launch {
            nightService.clearOverride(wakeDate)
            wakeAlarmCoordinator.reschedule()
        }
    }

    /** "Ignorieren" under a warning: gone for this day, and it stays gone. */
    fun dismissWarning(warning: PlannerWarning) {
        viewModelScope.launch { nightService.dismiss(date, warning) }
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
     * Only counted while the Flexibilität setting allows it — see `uiState`. A
     * tier that cannot be placed needs no waving: it is passed by itself.
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
