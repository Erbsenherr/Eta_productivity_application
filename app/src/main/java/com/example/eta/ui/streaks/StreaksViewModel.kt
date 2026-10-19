package com.example.eta.ui.streaks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eta.data.repository.PlanRepository
import com.example.eta.data.repository.RecurringTaskService
import com.example.eta.data.repository.SERIES_STREAK_LABEL
import com.example.eta.data.repository.SLEEP_STREAK_LABEL
import com.example.eta.data.repository.SleepLogRepository
import com.example.eta.data.repository.StreakRepository
import com.example.eta.domain.model.HabitStreak
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.StreakKind
import com.example.eta.domain.recurrence.RecurringGroup
import com.example.eta.domain.recurrence.groupRecurring
import com.example.eta.domain.streak.shownLength
import com.example.eta.domain.streak.streakOf
import com.example.eta.domain.streak.taskDoneOn
import com.example.eta.ui.attributes.RecurringAttributes
import com.example.eta.ui.attributes.toEdit
import kotlin.time.Clock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/** One streak as the tab draws it. */
data class StreakRow(
    /** Null for the series before its first evening as a streak — it is shown all the same. */
    val streak: HabitStreak?,
    val kind: StreakKind,
    val label: String,
    /** The flame's number: the length so far, and today's completion already in it. */
    val shown: Int,
    /** Today's part is done — the evening will count it. */
    val doneToday: Boolean,
)

data class StreaksUiState(
    /** The Tagesabschluss-Serie first, then the rest in the order they were begun. */
    val rows: List<StreakRow> = emptyList(),
    /** The standing schedule, one entry per task: what a streak can be begun for. */
    val groups: List<RecurringGroup> = emptyList(),
    /** Normalized names of the tasks that already carry a streak. */
    val taken: Set<String> = emptySet(),
    /** Whether the sleep streak is running already. */
    val sleepTaken: Boolean = false,
    /** Every live standing definition, for the overlap check of a new task. */
    val definitions: List<Item> = emptyList(),
)

/**
 * The Streaks tab (step 48).
 *
 * Nothing here moves a streak — that is the evening's, through
 * `ReevaluationService`. This screen begins them, ends them, and shows where
 * they stand, today's completion included.
 */
class StreaksViewModel(
    private val streakRepository: StreakRepository,
    private val recurringTaskService: RecurringTaskService,
    planRepository: PlanRepository,
    sleepLogRepository: SleepLogRepository,
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val today: LocalDate = clock.todayIn(timeZone)

    private val sources = combine(
        streakRepository.observeRunning(),
        recurringTaskService.observeDefinitions(),
        planRepository.observeDay(today),
        sleepLogRepository.observe(today.minus(DatePeriod(days = 1)), today),
        planRepository.observeSettledDates(),
    ) { streaks, definitions, blocks, logs, settled ->
        val sleepDone = logs[today]?.wokeOnTime == true &&
            logs[today.minus(DatePeriod(days = 1))]?.bedOnTime == true
        val series = streaks.firstOrNull { it.kind == StreakKind.SERIES }
        val seriesRun = streakOf(settled.toSet(), today)
        val seriesRow = StreakRow(
            streak = series,
            kind = StreakKind.SERIES,
            label = SERIES_STREAK_LABEL,
            shown = seriesRun.length,
            doneToday = !seriesRun.todayOpen,
        )
        val others = streaks.filter { it.kind != StreakKind.SERIES }.map { streak ->
            val done = when (streak.kind) {
                StreakKind.TASK -> streak.taskName?.let { taskDoneOn(it, blocks, timeZone) } == true
                StreakKind.SLEEP -> sleepDone
                StreakKind.SERIES -> false
            }
            StreakRow(
                streak = streak,
                kind = streak.kind,
                label = streak.label,
                shown = streak.shownLength(done, today),
                doneToday = done,
            )
        }
        StreaksUiState(
            rows = listOf(seriesRow) + others,
            groups = groupRecurring(definitions),
            taken = streaks.mapNotNullTo(mutableSetOf()) { it.taskName },
            sleepTaken = streaks.any { it.kind == StreakKind.SLEEP },
            definitions = definitions,
        )
    }

    val uiState: StateFlow<StreaksUiState> = sources.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StreaksUiState(),
    )

    /** "Streak beginnen" for a standing task. */
    fun beginTask(group: RecurringGroup) {
        val item = group.representative
        viewModelScope.launch { streakRepository.begin(item.normalizedName, item.name) }
    }

    /** "Streak beginnen" for getting up and going to bed on time, as one. */
    fun beginSleep() {
        viewModelScope.launch { streakRepository.begin(null, SLEEP_STREAK_LABEL) }
    }

    /** Ends a streak by hand — see `StreakRepository.end` for what that costs. */
    fun end(id: String) {
        viewModelScope.launch { streakRepository.end(id) }
    }

    /** A standing task made from the menu, with its streak begun at once. */
    fun createTaskWithStreak(name: String, note: String?, attributes: RecurringAttributes) {
        if (attributes.weekdays.isEmpty()) return
        viewModelScope.launch {
            recurringTaskService.createGroup(attributes.toEdit(name, note))
            streakRepository.begin(name, name)
        }
    }
}
