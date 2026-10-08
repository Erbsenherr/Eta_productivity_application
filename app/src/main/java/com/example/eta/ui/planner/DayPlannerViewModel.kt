package com.example.eta.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.AddItemResult
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.PlanRepository
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
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Priority
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.stampedWith
import com.example.eta.domain.model.withExtras
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.SPONTANEOUS_BREAK
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
import com.example.eta.domain.reward.SOCIAL_POINTS_PER_HOUR
import com.example.eta.domain.reward.plannedYield
import com.example.eta.ui.attributes.TodoAttributes
import com.example.eta.ui.subtasks.SubtaskSetting
import com.example.eta.ui.subtasks.addedDuration
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

/** What the second revolver offers. Fixed, and not backed by the database. */
enum class SpendKind(val label: String, val pointsPerHour: Double) {
    CUSTOM_EARN("Custom Earn", CUSTOM_EARN_POINTS_PER_HOUR),
    CUSTOM_SPEND("Custom Spend", CUSTOM_SPEND_POINTS_PER_HOUR),
    SOCIAL("Social", SOCIAL_POINTS_PER_HOUR),
}

/** Everything from the second revolver is an hour long until edited. */
val DEFAULT_SPEND_DURATION: Duration = 1.hours

/** What a break dragged in from the third revolver lasts until it is told otherwise. */
val PLANNED_BREAK: Duration = SPONTANEOUS_BREAK

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

    private val _breakPrompt = MutableStateFlow<BreakPrompt?>(null)
    val breakPrompt: StateFlow<BreakPrompt?> = _breakPrompt.asStateFlow()

    private val _mergeRequest = MutableStateFlow<MergeRequest?>(null)
    val mergeRequest: StateFlow<MergeRequest?> = _mergeRequest.asStateFlow()

    init {
        // The phase materializes what it is about to plan. Idempotent, so opening
        // the planner twice costs nothing.
        viewModelScope.launch { scheduleMaintenance.topUpUntil(date) }
    }

    /** How many priority tiers the user has waved past by hand. */
    private val skippedTiers = MutableStateFlow(0)

    /** The day, its plan and the steps inside its cards — read together. */
    private val dayState = combine(
        planRepository.observeDay(date),
        planRepository.observeDayPlan(date),
        subtaskRepository.observeByItem(),
        subtaskRepository.observeChecked(date),
    ) { blocks, dayPlan, subtasks, checked -> DayState(blocks, dayPlan, subtasks, checked) }

    val uiState: StateFlow<PlannerUiState> = combine(
        dayState,
        itemRepository.observeStage(Stage.WEEK),
        setupRepository.observe(),
        skippedTiers,
        itemRepository.observeFoldCandidates(),
    ) { read, weekItems, setup, skipped, foldable ->
        val (allBlocks, dayPlan, subtasks, checked) = read
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
        // Only the highest outstanding priority is offered; the rest waits.
        val tier = priorityTier(candidates, skipped)

        PlannerUiState(
            date = date,
            blocks = blocks,
            freed = freed,
            revolver = tier.items,
            revolverPriority = tier.priority,
            lockedPriorities = tier.lockedBelow,
            sleep = setup?.sleepStretches(date.dayOfWeek).orEmpty(),
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
        val existing = uiState.value.blocks.standing()
        val wanted = snapToGrid(atMinute).coerceIn(0, MINUTES_PER_DAY - 1)
        val travel = item.travelBefore
        val back = item.returnAfter

        val start = firstFreeStart(
            existing,
            duration,
            wanted,
            leadIn = travel,
            tailOut = back,
            tailOutExtra = breakAfter,
        )

        if (start == null) {
            // The break is the only part that may be given up. A journey — in
            // either direction — is time the task actually takes, and a task
            // planned without it is planned wrong.
            if (breakAfter != null &&
                fitsSomewhere(existing, duration, wanted, leadIn = travel, tailOut = back)
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
                returnAfter = back,
                breakAfter = breakAfter,
                origin = BlockOrigin.DRAGGED,
                createdAt = now,
                updatedAt = now,
            ).stampedWith(item),
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
            .filter { it.block.isMovable }
            .coveringMinute(toMinute.coerceIn(0, MINUTES_PER_DAY - 1))
            .firstOrNull()
        if (landedOn != null) {
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
        if (!entry.block.isMovable) return
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
            entries.forEach { entry ->
                if (entry.block.isMovable && entry.item.isOneOffBreak) {
                    planRepository.removeBlock(entry.block)
                    itemRepository.delete(entry.item)
                } else if (entry.block.isMovable && entry.item.type == ItemType.TODO) {
                    planRepository.removeBlock(entry.block)
                    if (entry.item.stage == Stage.DAY) itemRepository.moveTo(entry.item, Stage.WEEK)
                } else {
                    planRepository.discard(entry.block)
                }
            }
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
        val existing = uiState.value.blocks.standing()
        val wanted = snapToGrid(atMinute).coerceIn(0, MINUTES_PER_DAY - 1)
        val duration = attributes.duration
        val travel = attributes.travelBefore
        val back = attributes.returnAfter

        val start = firstFreeStart(
            existing,
            duration,
            wanted,
            leadIn = travel,
            tailOut = back,
            tailOutExtra = attributes.breakAfter,
        )
        if (start == null) {
            if (attributes.breakAfter != null &&
                fitsSomewhere(existing, duration, wanted, leadIn = travel, tailOut = back)
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
            returnAfter = back,
            breakAfter = attributes.breakAfter,
            endSound = attributes.endSound,
        ).withExtras(attributes.extras)

        val added = itemRepository.add(item, attributes.subtasks, attributes.foldedItemIds)
        if (added is AddItemResult.BlockedByLock) {
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
                returnAfter = back,
                breakAfter = attributes.breakAfter,
                origin = BlockOrigin.DRAGGED,
                createdAt = now,
                updatedAt = now,
            ).stampedWith(item),
        )
    }

    /** The second revolver: a named one-off that earns, spends or is simply social. */
    fun createSpend(kind: SpendKind, name: String, pointsPerHour: Double, atMinute: Int) {
        viewModelScope.launch {
            val existing = uiState.value.blocks.standing()
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

    /**
     * The third revolver: a break, placed like any card and worth nothing.
     *
     * The same card the dashboard slips in after a finished task —
     * `Item.spontaneousBreak`, `ItemRole.BREAK` and no category, so it neither
     * earns nor costs — only planned ahead instead of taken on the spot. A new
     * card per break, because the unique index allows an item one occurrence a
     * day and two breaks in one afternoon is nothing unusual. The Sperrliste is
     * not consulted: "Pause" is not an idea being hoarded.
     */
    fun createBreak(atMinute: Int) {
        viewModelScope.launch {
            val existing = uiState.value.blocks.standing()
            val wanted = snapToGrid(atMinute).coerceIn(0, MINUTES_PER_DAY - 1)
            val now = clock.now()
            val item = Item.spontaneousBreak(PLANNED_BREAK, now)

            val start = firstFreeStart(existing, PLANNED_BREAK, wanted)
            if (start == null) {
                _feedback.value = PlacementFeedback.NoRoom(item.name)
                return@launch
            }
            if (start != wanted) {
                _feedback.value = PlacementFeedback.Moved(item.name, minuteToLocalTime(start))
            }

            itemRepository.addWithoutLockCheck(item)
            planRepository.addBlock(
                PlannedBlock(
                    itemId = item.id,
                    date = date,
                    start = minuteToLocalTime(start),
                    plannedDuration = PLANNED_BREAK,
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
