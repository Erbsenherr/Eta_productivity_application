package com.example.eta.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.alarm.TaskStartCoordinator
import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.repository.ConflictRepository
import com.example.eta.data.repository.ContractRepository
import com.example.eta.data.repository.ItemRepository
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.PointsRepository
import com.example.eta.data.repository.SetupRepository
import com.example.eta.data.repository.SubtaskRepository
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.ContractState
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.planning.BlockConflict
import com.example.eta.domain.planning.DailyPhaseStatus
import com.example.eta.domain.planning.conflictsOf
import com.example.eta.domain.planning.EarlyBilling
import com.example.eta.domain.planning.SPONTANEOUS_BREAK
import com.example.eta.domain.planning.earlyBilling
import com.example.eta.domain.planning.confirmedRetroactively
import com.example.eta.domain.planning.nextAfterNow
import com.example.eta.domain.planning.pullForward
import com.example.eta.domain.planning.standing
import com.example.eta.domain.planning.PlanningPhase
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.canPlace
import com.example.eta.domain.planning.firstFreeStart
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.minuteToLocalTime
import com.example.eta.domain.planning.nextPlanning
import com.example.eta.domain.planning.claimsItsSlot
import com.example.eta.domain.planning.snapToGrid
import com.example.eta.domain.planning.withPomodoro
import com.example.eta.domain.planning.withoutPomodoro
import com.example.eta.domain.reevaluation.LEGACY_DAILY_CAP
import com.example.eta.domain.reward.plannedYield
import com.example.eta.domain.reward.yieldOf
import com.example.eta.domain.streak.Streak
import com.example.eta.domain.streak.streakOf
import com.example.eta.domain.staging.daysUntilBan
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/** A Sammelliste entry close to being banned, with the days it has left. */
data class CriticalTodo(
    val item: Item,
    val daysLeft: Int,
)

data class DashboardUiState(
    val today: LocalDate,
    val tomorrow: LocalDate,
    val balance: Double = 0.0,
    /** Earned today but not yet credited — the harvest happens in the evening. */
    val pendingHarvest: Double = 0.0,
    /** What today would harvest if everything still planned were done. */
    val plannedYield: Double = 0.0,
    val todayBlocks: List<BlockWithItem> = emptyList(),
    val tomorrowBlocks: List<BlockWithItem> = emptyList(),
    val deadlines: List<Item> = emptyList(),
    val critical: List<CriticalTodo> = emptyList(),
    /** What the running legacy contracts would pay in a day before the cap. */
    val legacyGrossPerDay: Double = 0.0,
    /** What the daily planning phase still owes. */
    val phase: DailyPhaseStatus? = null,
    /** The run of settled days — broken by any evening that was skipped. */
    val streak: Streak = Streak(length = 0, todayOpen = true),
    /**
     * Blocks standing on top of each other, on a day that is being lived.
     *
     * Today always, and tomorrow only once its plan is confirmed: before that
     * tomorrow is still being planned, and reporting a collision in a plan the
     * user is in the middle of making would be reporting their own work back to
     * them. Dismissed ones are already filtered out.
     */
    val conflicts: List<BlockConflict> = emptyList(),
    /** The steps inside each card that has any, by item id. */
    val subtasks: Map<String, List<Subtask>> = emptyMap(),
    /** Which steps today has ticked off, by block id. */
    val checked: Map<String, Set<String>> = emptyMap(),
) {
    /**
     * The crown: legacy contracts are worth more per day than the cap credits.
     * `Selbstverträge.md` asks for this to stand out — it is the sign that the
     * collection has outgrown what one day can pay for.
     */
    val crowned: Boolean get() = legacyGrossPerDay > LEGACY_DAILY_CAP
}

/**
 * What the list did with a correction it could not carry out as asked.
 *
 * The dashboard's list edits the same blocks the planner does, so it obeys the
 * same rule: nothing overlaps, and a change that would has to slide.
 */
sealed interface BlockEditFeedback {
    data class Moved(val name: String, val to: LocalTime) : BlockEditFeedback
    data class NoRoom(val name: String) : BlockEditFeedback

    /** A cancellation could not be taken back: the hours are somebody else's now. */
    data class SlotTaken(val name: String) : BlockEditFeedback

    data class Uncancelled(val name: String) : BlockEditFeedback
}

/**
 * What to do with the time a task has just freed by being finished.
 *
 * Asked after a confirmation, and only when the tick is about now rather than about
 * the past — see [confirmedRetroactively]. It names the next task, because a
 * question about "die darauffolgende Aufgabe" that does not say which one is a
 * question the user has to go and look up.
 */
data class FollowUpQuestion(
    /** The next task ahead, or null when the tick says nothing about one. */
    val next: NextTask? = null,
    /** How the task just finished is billed, or null when it was not finished early. */
    val billing: BillingChoice? = null,
)

data class NextTask(
    val blockId: String,
    val name: String,
    val plannedAt: LocalTime,
)

/**
 * A task finished before its planned end, and which of the two lengths it is
 * billed at — see [EarlyBilling].
 *
 * [full] starts out true and is **already written** when the question appears:
 * finishing early is not to be punished, so closing the dialog without touching
 * it must leave the generous answer standing rather than no answer at all.
 */
data class BillingChoice(
    val blockId: String,
    val name: String,
    val billing: EarlyBilling,
    val full: Boolean = true,
)

class DashboardViewModel(
    private val itemRepository: ItemRepository,
    private val planRepository: PlanRepository,
    private val pointsRepository: PointsRepository,
    contractRepository: ContractRepository,
    private val conflictRepository: ConflictRepository,
    private val setupRepository: SetupRepository,
    private val subtaskRepository: SubtaskRepository,
    private val taskStartCoordinator: TaskStartCoordinator,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val today: LocalDate = clock.todayIn(timeZone)
    private val tomorrow: LocalDate = today.plus(DatePeriod(days = 1))

    private val _blockEditFeedback = MutableStateFlow<BlockEditFeedback?>(null)
    val blockEditFeedback: StateFlow<BlockEditFeedback?> = _blockEditFeedback.asStateFlow()

    private val _followUp = MutableStateFlow<FollowUpQuestion?>(null)
    val followUp: StateFlow<FollowUpQuestion?> = _followUp.asStateFlow()

    val uiState: StateFlow<DashboardUiState> = combine(
        pointsRepository.observeBalance(),
        planRepository.observeDay(today),
        planRepository.observeDay(tomorrow),
        itemRepository.observeOpenDeadlines(),
        itemRepository.observeCriticalCollectionItems(),
    ) { balance, todayBlocks, tomorrowBlocks, deadlines, criticalItems ->
        val now = clock.now()
        DashboardUiState(
            today = today,
            tomorrow = tomorrow,
            balance = balance,
            pendingHarvest = todayBlocks
                .filter { it.block.isCompleted }
                .sumOf { yieldOf(it.item, it.block) },
            plannedYield = plannedYield(todayBlocks),
            todayBlocks = todayBlocks,
            tomorrowBlocks = tomorrowBlocks,
            deadlines = deadlines,
            critical = criticalItems.mapNotNull { item ->
                item.daysUntilBan(now, timeZone)?.let { CriticalTodo(item, it) }
            },
        )
    }.combine(phaseStatus()) { state, phase ->
        state.copy(phase = phase)
    }.combine(planRepository.observeSettledDates()) { state, settled ->
        state.copy(streak = streakOf(settled.toSet(), today))
    }.combine(contractRepository.observeAll()) { state, contracts ->
        // Combined separately rather than as a sixth source: combine is typed only
        // up to five, and folding it in keeps the state assembly readable.
        state.copy(
            legacyGrossPerDay = contracts
                .filter { it.state == ContractState.LEGACY }
                .sumOf { it.dailyPayout },
        )
    }.combine(subtaskRepository.observeByItem()) { state, subtasks ->
        state.copy(subtasks = subtasks)
    }.combine(subtaskRepository.observeChecked(today)) { state, checked ->
        state.copy(checked = checked)
    }.combine(conflictRepository.observeDismissed(today)) { state, dismissed ->
        // Read off the blocks each time rather than stored: a collision is a fact
        // about where things stand, and moving one of the two blocks has to make
        // it go away by itself.
        val days = buildList {
            add(state.todayBlocks)
            if (state.phase?.tomorrowConfirmed == true) add(state.tomorrowBlocks)
        }
        state.copy(
            conflicts = days.flatMap { conflictsOf(it) }.filterNot { it.key in dismissed },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(today = today, tomorrow = tomorrow),
    )

    init {
        // Answers about blocks nobody will see again. Here rather than on a
        // schedule of its own: the dashboard is opened every day anyway.
        viewModelScope.launch { conflictRepository.prune(today) }
    }

    /**
     * "Ignorieren": stop mentioning this one collision.
     *
     * For this day only, which is what the two block ids already say — one item
     * has one block per day, so the same two tasks colliding tomorrow are a
     * different pair and will ask again.
     */
    fun dismissConflict(conflict: BlockConflict) {
        viewModelScope.launch { conflictRepository.dismiss(conflict.key, conflict.date) }
    }

    /**
     * What the daily phase still owes, live.
     *
     * Built from the two day plans rather than asked once: planning tomorrow at
     * lunchtime should change what the dashboard says the moment it happens.
     */
    private fun phaseStatus(): Flow<DailyPhaseStatus> = combine(
        planRepository.observeDayPlan(today),
        planRepository.observeDayPlan(tomorrow),
        planRepository.observeDay(tomorrow),
        setupRepository.observe(),
    ) { todayPlan, tomorrowPlan, tomorrowBlocks, setup ->
        DailyPhaseStatus(
            todaySettled = todayPlan?.isSettled == true,
            tomorrowConfirmed = tomorrowPlan?.isConfirmed == true,
            // Only hand-placed blocks count as "already planned": the standing
            // schedule materializes itself and would otherwise always look done.
            tomorrowHasBlocks = tomorrowBlocks.any { it.block.origin == BlockOrigin.DRAGGED },
            dueAt = setup?.let {
                nextPlanning(it, PlanningPhase.DAILY, clock.now().toLocalDateTime(timeZone))
            },
            owedFrom = setup?.let { LocalDateTime(today, it.dailyPlanningTime) },
        )
    }

    fun toggleCompleted(entry: BlockWithItem) {
        viewModelScope.launch {
            if (entry.block.isCompleted) {
                // The billed length belongs to the tick being taken back: one set
                // by finishing early would otherwise survive and bill a task that
                // then runs its full course at the shortened figure.
                planRepository.reopen(entry.block.copy(actualDuration = null))
            } else {
                // Only a tick on the day itself can be early; one on another day
                // is being caught up, and the clock says nothing about it.
                val billing = if (entry.block.date == today) {
                    earlyBilling(entry.block, nowMinute())
                } else {
                    null
                }
                planRepository.complete(entry.block, actualDuration = billing?.full)
                askFollowUp(entry, billing)
            }
            // Something ticked off ahead of its hour should not still announce
            // itself, and reopening one puts it back in the queue.
            taskStartCoordinator.reschedule()
        }
    }

    /** One step of a group, ticked off on this day only. */
    fun setSubtaskChecked(blockId: String, subtaskId: String, checked: Boolean) {
        viewModelScope.launch { subtaskRepository.setChecked(blockId, subtaskId, checked) }
    }

    /**
     * Puts the question about the next task, where the tick was about now.
     *
     * Silent about the next task in three cases, each for its own reason: a block
     * on another day is being caught up rather than lived; a tick the day has
     * already moved past says nothing about what comes next; and a day with nothing
     * left ahead has no next task to ask about.
     *
     * [billing] is the other half and independent of all three: a task finished
     * early is asked how it is billed even when it was the last of the day.
     */
    private fun askFollowUp(entry: BlockWithItem, billing: EarlyBilling?) {
        if (entry.block.date != today) return
        val blocks = uiState.value.todayBlocks
        val minute = nowMinute()
        val next = if (confirmedRetroactively(blocks, entry.block, minute)) {
            null
        } else {
            nextAfterNow(blocks.filterNot { it.block.id == entry.block.id }, minute)
        }
        if (next == null && billing == null) return
        _followUp.value = FollowUpQuestion(
            next = next?.let { NextTask(it.block.id, it.item.name, it.block.start) },
            billing = billing?.let { BillingChoice(entry.block.id, entry.item.name, it) },
        )
    }

    /** "Plan beibehalten", and what closing the question means. */
    fun dismissFollowUp() {
        _followUp.value = null
    }

    /**
     * "Voll abrechnen" or "Nur die gebrauchte Zeit", for the task just finished
     * early. Written at once rather than on closing, so the dialog's three ways
     * out need not each remember to save it.
     */
    fun setBilledInFull(full: Boolean) {
        val question = _followUp.value ?: return
        val choice = question.billing ?: return
        if (choice.full == full) return
        _followUp.value = question.copy(billing = choice.copy(full = full))
        viewModelScope.launch {
            // Read back by id: the row the tick wrote, not the one the list showed.
            val block = planRepository.findBlock(choice.blockId) ?: return@launch
            if (!block.isCompleted) return@launch
            planRepository.addBlock(
                block.copy(
                    actualDuration = if (full) choice.billing.full else choice.billing.used,
                    updatedAt = clock.now(),
                ),
            )
        }
    }

    /**
     * "Task vorziehen", with or without a break in front of it.
     *
     * Only the next task moves, and only as far as it fits — see [pullForward]. It
     * refuses rather than improvises: if there is no room for the break, or the task
     * cannot start any earlier than it already does, the plan stays as it is and the
     * screen says so.
     */
    fun pullNextForward(withBreak: Boolean) {
        val question = _followUp.value?.next ?: return
        _followUp.value = null
        viewModelScope.launch {
            val block = planRepository.findBlock(question.blockId) ?: return@launch
            val pull = pullForward(
                obstacles = uiState.value.todayBlocks.standing(),
                block = block,
                fromMinute = nowMinute(),
                withBreak = if (withBreak) SPONTANEOUS_BREAK else null,
            )
            if (pull == null) {
                _blockEditFeedback.value = BlockEditFeedback.NoRoom(question.name)
                return@launch
            }

            val now = clock.now()
            pull.breakStart?.let { start ->
                val item = Item.spontaneousBreak(SPONTANEOUS_BREAK, now)
                itemRepository.addWithoutLockCheck(item)
                planRepository.addBlock(
                    PlannedBlock(
                        itemId = item.id,
                        date = today,
                        start = minuteToLocalTime(start),
                        plannedDuration = SPONTANEOUS_BREAK,
                        origin = BlockOrigin.DRAGGED,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            planRepository.addBlock(
                block.copy(start = minuteToLocalTime(pull.taskStart), updatedAt = now),
            )
            _blockEditFeedback.value =
                BlockEditFeedback.Moved(question.name, minuteToLocalTime(pull.taskStart))
            taskStartCoordinator.reschedule()
        }
    }

    private fun nowMinute(): Int = clock.now().toLocalDateTime(timeZone).time.minuteOfDay()

    /** Correcting how long something really took; feeds the evening harvest. */
    fun setActualDuration(block: PlannedBlock, duration: Duration) {
        viewModelScope.launch {
            planRepository.addBlock(
                block.copy(
                    actualDuration = duration.coerceAtLeast(Duration.ZERO),
                    updatedAt = clock.now(),
                ),
            )
        }
    }

    /**
     * Moving something the day already holds, without opening the planner.
     *
     * The list only offered the duration before, which meant the one correction
     * a running day most often needs — this starts later than planned — had to go
     * through the planner. It runs the planner's own `canPlace` / `firstFreeStart`
     * pair rather than writing the time blindly: the list must not become a way
     * around the one rule the planner enforces.
     */
    fun setStart(entry: BlockWithItem, start: LocalTime) {
        viewModelScope.launch {
            // A cancellation holds nothing and a finished task has released its
            // stretch, so neither is an obstacle.
            val onTheDay = uiState.value.todayBlocks.filter { it.claimsItsSlot() }.map { it.block }
            val wanted = snapToGrid(start.minuteOfDay()).coerceIn(0, MINUTES_PER_DAY - 1)
            val moved = entry.block.copy(
                start = minuteToLocalTime(wanted),
                updatedAt = clock.now(),
            )

            if (canPlace(onTheDay, moved)) {
                planRepository.addBlock(moved)
                // The dashboard is the one place a block moves outside a flow, so
                // the alarm cannot wait for `rescheduleAlarms` to come round.
                taskStartCoordinator.reschedule()
                return@launch
            }
            val free = firstFreeStart(
                existing = onTheDay,
                duration = entry.block.effectiveDuration,
                preferredStart = wanted,
                ignoreId = entry.block.id,
                // The whole container has to fit, journeys and break included —
                // the same rule the planner enforces, so this stays a correction
                // rather than a back door around it.
                leadIn = entry.block.travelBefore,
                tailOut = entry.block.returnAfter,
                tailOutExtra = entry.block.breakAfter,
            )
            if (free == null) {
                _blockEditFeedback.value = BlockEditFeedback.NoRoom(entry.item.name)
                return@launch
            }
            planRepository.addBlock(moved.copy(start = minuteToLocalTime(free)))
            _blockEditFeedback.value =
                BlockEditFeedback.Moved(entry.item.name, minuteToLocalTime(free))
            taskStartCoordinator.reschedule()
        }
    }

    /**
     * Takes a cancellation back.
     *
     * **Refuses rather than slides** when the hours have since been given to
     * something else. A drop is the user choosing a time, and the plan may help by
     * finding the nearest one; this is a claim about a slot that was already
     * theirs, and if it is gone the honest answer is to say so rather than to put
     * the block somewhere nobody asked for.
     */
    fun uncancel(entry: BlockWithItem) {
        viewModelScope.launch {
            val restored = entry.block.copy(discardedAt = null)
            val standing = uiState.value.todayBlocks
                .filter { it.block.id != entry.block.id && it.claimsItsSlot() }
                .map { it.block }

            if (!canPlace(standing, restored)) {
                _blockEditFeedback.value = BlockEditFeedback.SlotTaken(entry.item.name)
                return@launch
            }
            planRepository.undiscard(entry.block)
            _blockEditFeedback.value = BlockEditFeedback.Uncancelled(entry.item.name)
            taskStartCoordinator.reschedule()
        }
    }

    fun dismissBlockEditFeedback() {
        _blockEditFeedback.value = null
    }

    /**
     * Sets a pomodoro rhythm on a block.
     *
     * [fromNow] is the "Gerade" box: a task already running counts its rhythm from
     * this minute. From "Als Nächstes" it counts from the task's own start, and the
     * start keeps its ordinary sound. The block is looked up by id — the box's copy
     * may be older than the last edit, and writing it back would undo that edit.
     */
    fun setPomodoro(blockId: String, work: Duration, pause: Duration, fromNow: Boolean) {
        viewModelScope.launch {
            val block = planRepository.findBlock(blockId) ?: return@launch
            val now = clock.now().toLocalDateTime(timeZone)
            val here = if (fromNow && block.date == now.date) now.time else null
            planRepository.addBlock(
                block.withPomodoro(work, pause, here).copy(updatedAt = clock.now()),
            )
            taskStartCoordinator.reschedule()
        }
    }

    /** Switches the rhythm off again; the task goes on without it. */
    fun clearPomodoro(blockId: String) {
        viewModelScope.launch {
            val block = planRepository.findBlock(blockId) ?: return@launch
            planRepository.addBlock(block.withoutPomodoro().copy(updatedAt = clock.now()))
            taskStartCoordinator.reschedule()
        }
    }

    /**
     * A correction to the account, by hand.
     *
     * A row like any other, because the balance *is* the ledger: booking a
     * manual amount leaves the history readable instead of overwriting a number
     * whose provenance is then gone. Positive credits, negative takes away.
     */
    fun adjustPoints(amount: Double, note: String?) {
        if (amount == 0.0) return
        viewModelScope.launch {
            pointsRepository.record(
                amount = amount,
                reason = PointsReason.MANUAL,
                note = note?.takeIf { it.isNotBlank() },
            )
        }
    }
}
