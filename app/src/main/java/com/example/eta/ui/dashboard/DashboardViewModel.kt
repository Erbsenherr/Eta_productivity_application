package com.example.eta.ui.dashboard

import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.SubtaskTimer
import com.example.eta.domain.subtask.isGrowingStep
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
import com.example.eta.data.repository.NightService
import com.example.eta.data.repository.SleepLogRepository
import com.example.eta.domain.model.NightOverride
import com.example.eta.domain.model.SleepLog
import com.example.eta.domain.setup.UserSetup
import kotlinx.datetime.minus
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.ContractState
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
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
import com.example.eta.domain.planning.endMinute
import com.example.eta.domain.planning.fullTimerGoal
import com.example.eta.domain.planning.timerBilled
import com.example.eta.domain.planning.timerCleared
import com.example.eta.domain.planning.timerElapsed
import com.example.eta.domain.planning.timerOverflow
import com.example.eta.domain.planning.timerPaused
import com.example.eta.domain.planning.timerResumed
import com.example.eta.domain.planning.timerStarted
import com.example.eta.domain.planning.timerWasUsed
import com.example.eta.domain.growth.isGrowthTask
import com.example.eta.domain.growth.timerBufferOrZero
import com.example.eta.domain.planning.withoutPomodoro
import com.example.eta.domain.reevaluation.LEGACY_DAILY_CAP
import com.example.eta.domain.reward.plannedYield
import com.example.eta.domain.reward.yieldOf
import com.example.eta.domain.streak.Streak
import com.example.eta.domain.streak.streakOf
import com.example.eta.domain.setup.DEFAULT_PUNCTUAL_TOLERANCE
import com.example.eta.domain.staging.daysUntilBan
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
    /** When each of them was ticked, by block id and step — a routine shows it. */
    val checkTimes: Map<String, Map<String, Instant>> = emptyMap(),
    /** Step 49: the timers of routine steps today, by block and then by step. */
    val stepTimers: Map<String, Map<String, SubtaskTimer>> = emptyMap(),
) {
    /**
     * The crown: legacy contracts are worth more per day than the cap credits.
     * `Selbstverträge.md` asks for this to stand out — it is the sign that the
     * collection has outgrown what one day can pay for.
     */
    val crowned: Boolean get() = legacyGrossPerDay > LEGACY_DAILY_CAP
}

/** The standing nights, the ones changed by hand, and what was said about yesterday and today. */
data class SleepCheckState(
    val setup: UserSetup,
    val overrides: Map<LocalDate, NightOverride>,
    val logs: Map<LocalDate, SleepLog>,
)

/**
 * A Timer-Task started so late that its time would run past its own block.
 *
 * What the dialog shows; the answer is worked out again when it is given.
 */
data class TimerOverflowPrompt(
    val blockId: String,
    val name: String,
    val goal: Duration,
    /** When the block ends as planned, and when the full time would be through. */
    val frameEnd: LocalTime,
    val projectedEnd: LocalTime,
    /** What the timer would count if it stops with the block; null when that is over. */
    val shortenedGoal: Duration?,
    /** Whether the block can be lengthened to [projectedEnd]. */
    val canExtend: Boolean,
    /** What stands in the stretch behind the block, if anything does. */
    val nextName: String?,
    /** When that task would begin instead, where it can give way. */
    val pushedStart: LocalTime?,
)

/** A timer ended short of its time, for the screen to say what that means. */
data class TimerResult(
    val name: String,
    val worked: Duration,
    val goal: Duration,
    /** A "Zielzeit" is being worked towards, and this sitting does not count. */
    val missesIncrement: Boolean,
    /** The block's frame had run out — the case a larger buffer answers. */
    val frameOver: Boolean,
)

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
    /**
     * The two things the morning and night boxes are read from. Null in the
     * tutorial's practice app, which has a simulated day and nothing to ask
     * about anyone's sleep.
     */
    private val sleepLogRepository: SleepLogRepository? = null,
    private val nightService: NightService? = null,
) : ViewModel() {

    private val today: LocalDate = clock.todayIn(timeZone)
    private val tomorrow: LocalDate = today.plus(DatePeriod(days = 1))

    private val _blockEditFeedback = MutableStateFlow<BlockEditFeedback?>(null)
    val blockEditFeedback: StateFlow<BlockEditFeedback?> = _blockEditFeedback.asStateFlow()

    private val _followUp = MutableStateFlow<FollowUpQuestion?>(null)
    val followUp: StateFlow<FollowUpQuestion?> = _followUp.asStateFlow()

    private val _timerOverflow = MutableStateFlow<TimerOverflowPrompt?>(null)
    val timerOverflow: StateFlow<TimerOverflowPrompt?> = _timerOverflow.asStateFlow()

    private val _timerResult = MutableStateFlow<TimerResult?>(null)
    val timerResult: StateFlow<TimerResult?> = _timerResult.asStateFlow()

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
    }.combine(subtaskRepository.observeCheckTimes(today)) { state, times ->
        state.copy(checked = times.mapValues { it.value.keys }, checkTimes = times)
    }.combine(subtaskRepository.observeTimers(today)) { state, timers ->
        state.copy(stepTimers = timers)
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
                // A Timer-Task's timer goes with it: the sitting starts over.
                planRepository.reopen(entry.block.copy(actualDuration = null).timerCleared())
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
        viewModelScope.launch {
            subtaskRepository.setChecked(blockId, subtaskId, checked)
            // A step's timer may have gone with the tick, and with it its alarm.
            taskStartCoordinator.reschedule()
        }
    }

    // --- the timer of a routine's step (step 49) -----------------------------

    private fun stepOf(subtaskId: String): Subtask? =
        uiState.value.subtasks.values.asSequence().flatten().firstOrNull { it.id == subtaskId }

    /** "Start" on a Timer-Task step: its whole time, from now. */
    fun startStepTimer(blockId: String, subtaskId: String) {
        viewModelScope.launch {
            val step = stepOf(subtaskId) ?: return@launch
            val goal = step.duration ?: return@launch
            if (subtaskRepository.findTimer(blockId, subtaskId) != null) return@launch
            subtaskRepository.startTimer(blockId, subtaskId, goal)
            taskStartCoordinator.reschedule()
        }
    }

    fun pauseStepTimer(blockId: String, subtaskId: String) {
        viewModelScope.launch {
            subtaskRepository.pauseTimer(blockId, subtaskId)
            taskStartCoordinator.reschedule()
        }
    }

    fun resumeStepTimer(blockId: String, subtaskId: String) {
        viewModelScope.launch {
            subtaskRepository.resumeTimer(blockId, subtaskId)
            taskStartCoordinator.reschedule()
        }
    }

    /**
     * "Ende" on a step: the count is kept, the step ticked, the routine moves
     * on. Ended short, the screen says so — as for a task — and whether the
     * "Zielzeit" lets it count.
     */
    fun endStepTimer(blockId: String, subtaskId: String) {
        viewModelScope.launch {
            val step = stepOf(subtaskId) ?: return@launch
            subtaskRepository.endTimer(blockId, subtaskId)
            taskStartCoordinator.reschedule()
            val timer = subtaskRepository.findTimer(blockId, subtaskId) ?: return@launch
            if (timer.worked < timer.goal) {
                _timerResult.value = TimerResult(
                    name = step.name,
                    worked = timer.worked,
                    goal = timer.goal,
                    missesIncrement = step.isGrowingStep &&
                        setupRepository.find()?.allowPartialTimerGrowth != true,
                    frameOver = false,
                )
            }
        }
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

    // --- getting up and going to bed -----------------------------------------

    /**
     * What "Guten Morgen!" and "Gute Nacht!" are worked out from. The screen
     * does the working out, against its own ticking clock — a window opens and
     * closes by the minute, and no row changes when it does.
     */
    val sleepCheck: StateFlow<SleepCheckState?> =
        if (sleepLogRepository == null || nightService == null) {
            MutableStateFlow(null)
        } else {
            combine(
                setupRepository.observe(),
                nightService.observeOverrides(),
                sleepLogRepository.observe(today.minus(DatePeriod(days = 1)), today),
            ) { setup, overrides, logs -> setup?.let { SleepCheckState(it, overrides, logs) } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

    /**
     * "Bin … aufgestanden". [at] is null for "jetzt"; [due] is the hour that
     * applied this morning, stored beside the answer so a later change to the
     * settings cannot turn an on-time morning into a late one.
     */
    fun recordWake(at: LocalTime?, due: LocalTime) {
        val repository = sleepLogRepository ?: return
        viewModelScope.launch {
            val time = at ?: clock.now().toLocalDateTime(timeZone).time.let { LocalTime(it.hour, it.minute) }
            // The tolerance that applies now, stored with the answer like the hour.
            val tolerance = sleepCheck.value?.setup?.punctualTolerance ?: DEFAULT_PUNCTUAL_TOLERANCE
            repository.recordWake(today, time, due, tolerance)
        }
    }

    /** "Gehe nun schlafen", or the next day's answer about [day]'s evening. */
    fun recordBed(day: LocalDate, onTime: Boolean) {
        val repository = sleepLogRepository ?: return
        viewModelScope.launch { repository.recordBed(day, onTime) }
    }

    // --- Timer-Tasks ---------------------------------------------------------

    /** Today's entry by id, with the block read fresh — the list's copy may be older. */
    private suspend fun timerEntry(blockId: String): BlockWithItem? {
        val listed = uiState.value.todayBlocks.firstOrNull { it.block.id == blockId } ?: return null
        val block = planRepository.findBlock(blockId) ?: return null
        return listed.copy(block = block)
    }

    /**
     * "Start": the timer of a Timer-Task begins to count.
     *
     * Begun so late that its time would run into the next task, it asks first —
     * see [TimerOverflowPrompt]. The buffer is what usually makes that question
     * unnecessary.
     */
    fun startTimer(blockId: String) {
        viewModelScope.launch {
            val entry = timerEntry(blockId) ?: return@launch
            if (!entry.block.isOpen || entry.block.timerWasUsed) return@launch
            val goal = entry.block.fullTimerGoal(entry.item.timerBufferOrZero)
            val minute = nowMinute()
            val overflow = timerOverflow(uiState.value.todayBlocks, entry, minute, goal)
                ?.takeIf { it.shortenedGoal != null || it.canExtend }
            if (overflow == null) {
                beginTimer(entry.block, goal)
                return@launch
            }
            _timerOverflow.value = TimerOverflowPrompt(
                blockId = blockId,
                name = entry.item.name,
                goal = goal,
                frameEnd = minuteToLocalTime(entry.block.endMinute()),
                projectedEnd = minuteToLocalTime(overflow.projectedEnd),
                shortenedGoal = overflow.shortenedGoal,
                canExtend = overflow.canExtend,
                nextName = overflow.next?.item?.name,
                pushedStart = overflow.pushedNext?.start,
            )
        }
    }

    /**
     * The answer to a late start: the timer stops with its block ([shorten]),
     * or the block is lengthened — and whatever stands behind it begins later.
     * Worked out again from the day as it now stands: a minute has passed since
     * the question was put.
     */
    fun answerTimerOverflow(shorten: Boolean) {
        val prompt = _timerOverflow.value ?: return
        _timerOverflow.value = null
        viewModelScope.launch {
            val entry = timerEntry(prompt.blockId) ?: return@launch
            val block = entry.block
            if (!block.isOpen || block.timerWasUsed) return@launch
            val goal = block.fullTimerGoal(entry.item.timerBufferOrZero)
            val overflow = timerOverflow(uiState.value.todayBlocks, entry, nowMinute(), goal)
            when {
                overflow == null -> beginTimer(block, goal)
                shorten -> beginTimer(block, overflow.shortenedGoal ?: goal)
                !overflow.canExtend -> beginTimer(block, overflow.shortenedGoal ?: goal)
                else -> {
                    overflow.pushedNext?.let { planRepository.addBlock(it.copy(updatedAt = clock.now())) }
                    // Longer by exactly the lateness the buffer could not take:
                    // the timer's own time stays what it was.
                    val extension = (overflow.projectedEnd - block.endMinute()).minutes
                    beginTimer(
                        block.copy(
                            plannedDuration = block.plannedDuration + extension,
                            timerExtension = extension,
                        ),
                        goal,
                    )
                }
            }
        }
    }

    fun dismissTimerOverflow() {
        _timerOverflow.value = null
    }

    private suspend fun beginTimer(block: PlannedBlock, goal: Duration) {
        val now = clock.now()
        planRepository.addBlock(block.timerStarted(goal, now).copy(updatedAt = now))
        // The pomodoro turns and the goal ring off the timer, so the alarm has
        // to be aimed again whenever it starts, stops or goes on.
        taskStartCoordinator.reschedule()
    }

    /** "Pause": a break by hand, which is not counted. */
    fun pauseTimer(blockId: String) = changeTimer(blockId) { block, now -> block.timerPaused(now) }

    /** "Weiter". */
    fun resumeTimer(blockId: String) = changeTimer(blockId) { block, now -> block.timerResumed(now) }

    private fun changeTimer(blockId: String, change: (PlannedBlock, Instant) -> PlannedBlock) {
        viewModelScope.launch {
            val block = planRepository.findBlock(blockId) ?: return@launch
            if (!block.isOpen || !block.timerWasUsed) return@launch
            val now = clock.now()
            planRepository.addBlock(change(block, now).copy(updatedAt = now))
            taskStartCoordinator.reschedule()
        }
    }

    /**
     * "Ende": the sitting is over, and what the timer counted is what it was.
     *
     * Worked in full, it is a completion like any other and the day asks what
     * to do with the time. Ended short, it is billed at what was counted and
     * does not count towards the next increment — and the screen says so, with
     * the advice about the buffer if the frame had run out.
     */
    fun endTimer(blockId: String) {
        viewModelScope.launch {
            val entry = timerEntry(blockId) ?: return@launch
            val block = entry.block
            if (!block.isOpen || !block.timerWasUsed) return@launch

            val now = clock.now()
            val buffer = entry.item.timerBufferOrZero
            val goal = block.fullTimerGoal(buffer)
            val counted = block.timerElapsed(now)
            val full = counted >= goal
            planRepository.complete(
                block.timerPaused(now).copy(actualDuration = null),
                // Null is "as planned" — which a block lengthened by a late
                // start is not billed at, worked in full or not.
                actualDuration = block.timerBilled(now, buffer).takeIf { it < block.plannedDuration },
            )
            taskStartCoordinator.reschedule()

            if (full) {
                askFollowUp(entry, billing = null)
            } else {
                _timerResult.value = TimerResult(
                    name = entry.item.name,
                    worked = counted,
                    goal = goal,
                    missesIncrement = entry.item.isGrowthTask &&
                        setupRepository.find()?.allowPartialTimerGrowth != true,
                    frameOver = nowMinute() >= block.endMinute(),
                )
            }
        }
    }

    fun dismissTimerResult() {
        _timerResult.value = null
    }

    /**
     * A Timer-Task ticked off without its timer, after the screen has asked.
     *
     * [worked] is the time the user says was put in, or null for all of it. It
     * is stored the way the timer stores its own count — plus the buffer — so
     * the evening bills it and decides about the increment by the one rule.
     */
    fun completeTimerByHand(blockId: String, worked: Duration?) {
        viewModelScope.launch {
            val entry = timerEntry(blockId) ?: return@launch
            val block = entry.block
            if (!block.isOpen) return@launch
            val buffer = entry.item.timerBufferOrZero
            val billed = worked
                ?.takeIf { it < block.fullTimerGoal(buffer) }
                ?.let { (it + buffer).coerceAtMost(block.plannedDuration) }
            planRepository.complete(block.copy(actualDuration = null), actualDuration = billed)
            taskStartCoordinator.reschedule()
            askFollowUp(entry, billing = null)
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
