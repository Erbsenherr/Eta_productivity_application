package com.example.erik_iteration_2.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.erik_iteration_2.alarm.TaskStartCoordinator
import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.data.repository.ContractRepository
import com.example.erik_iteration_2.data.repository.ItemRepository
import com.example.erik_iteration_2.data.repository.PlanRepository
import com.example.erik_iteration_2.data.repository.PointsRepository
import com.example.erik_iteration_2.data.repository.SetupRepository
import com.example.erik_iteration_2.domain.model.BlockOrigin
import com.example.erik_iteration_2.domain.model.ContractState
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.model.PointsReason
import com.example.erik_iteration_2.domain.planning.DailyPhaseStatus
import com.example.erik_iteration_2.domain.planning.PlanningPhase
import com.example.erik_iteration_2.domain.planning.MINUTES_PER_DAY
import com.example.erik_iteration_2.domain.planning.canPlace
import com.example.erik_iteration_2.domain.planning.firstFreeStart
import com.example.erik_iteration_2.domain.planning.minuteOfDay
import com.example.erik_iteration_2.domain.planning.minuteToLocalTime
import com.example.erik_iteration_2.domain.planning.nextPlanning
import com.example.erik_iteration_2.domain.planning.snapToGrid
import com.example.erik_iteration_2.domain.reevaluation.LEGACY_DAILY_CAP
import com.example.erik_iteration_2.domain.reward.yieldOf
import com.example.erik_iteration_2.domain.streak.Streak
import com.example.erik_iteration_2.domain.streak.streakOf
import com.example.erik_iteration_2.domain.staging.daysUntilBan
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
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

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
}

class DashboardViewModel(
    private val itemRepository: ItemRepository,
    private val planRepository: PlanRepository,
    private val pointsRepository: PointsRepository,
    contractRepository: ContractRepository,
    private val setupRepository: SetupRepository,
    private val taskStartCoordinator: TaskStartCoordinator,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val today: LocalDate = clock.now().toLocalDateTime(timeZone).date
    private val tomorrow: LocalDate = today.plus(DatePeriod(days = 1))

    private val _blockEditFeedback = MutableStateFlow<BlockEditFeedback?>(null)
    val blockEditFeedback: StateFlow<BlockEditFeedback?> = _blockEditFeedback.asStateFlow()

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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(today = today, tomorrow = tomorrow),
    )

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
        )
    }

    fun toggleCompleted(entry: BlockWithItem) {
        viewModelScope.launch {
            if (entry.block.isCompleted) {
                planRepository.reopen(entry.block)
            } else {
                planRepository.complete(entry.block)
            }
            // Something ticked off ahead of its hour should not still announce
            // itself, and reopening one puts it back in the queue.
            taskStartCoordinator.reschedule()
        }
    }

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
            val onTheDay = uiState.value.todayBlocks.map { it.block }
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
                // The whole container has to fit, journey and break included —
                // the same rule the planner enforces, so this stays a correction
                // rather than a back door around it.
                leadIn = entry.block.travelBefore,
                tailOut = entry.block.breakAfter,
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

    fun dismissBlockEditFeedback() {
        _blockEditFeedback.value = null
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
