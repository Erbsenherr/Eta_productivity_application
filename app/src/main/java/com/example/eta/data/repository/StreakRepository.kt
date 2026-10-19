package com.example.eta.data.repository

import com.example.eta.data.local.StreakDao
import com.example.eta.domain.model.HabitStreak
import com.example.eta.domain.model.PointsReason
import com.example.eta.domain.model.StreakKind
import com.example.eta.domain.model.normalizeName
import com.example.eta.domain.streak.STREAK_BREAK_PENALTY
import com.example.eta.domain.streak.StreakOutcome
import com.example.eta.domain.streak.StreakVerdict
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** What the sleep streak is called on the tab and in the ledger. */
const val SLEEP_STREAK_LABEL = "Pünktlich schlafen & aufstehen"

/** What the Tagesabschluss-Serie is called on the tab and in the ledger. */
const val SERIES_STREAK_LABEL = "Tagesabschluss"

/**
 * The Streaks tab's rows (step 48).
 *
 * The evening's verdicts are worked out in `domain/streak/HabitStreaks.kt` and
 * written by [apply], which `ReevaluationService.settle` alone calls — a streak
 * moves when the day is settled, like the harvest.
 */
class StreakRepository(
    private val dao: StreakDao,
    private val pointsRepository: PointsRepository,
    private val clock: Clock = Clock.System,
) {

    fun observeRunning(): Flow<List<HabitStreak>> = dao.observeRunning()

    suspend fun running(): List<HabitStreak> = dao.running()

    /**
     * "Streak beginnen" for a standing task, by its name — or for the sleep
     * streak with a null [taskName]. A task already carrying a running streak
     * gets no second one.
     */
    suspend fun begin(taskName: String?, label: String) {
        val kind = if (taskName == null) StreakKind.SLEEP else StreakKind.TASK
        val name = taskName?.let(::normalizeName)
        val exists = dao.running().any { it.kind == kind && it.taskName == name }
        if (exists) return
        val now = clock.now()
        dao.upsert(
            HabitStreak(kind = kind, taskName = name, label = label.trim(), startedAt = now, updatedAt = now),
        )
    }

    /**
     * Ends a streak by hand. **A streak that has begun counting is broken by
     * this**, and costs what a break costs — otherwise ending one on the
     * afternoon it was going to break would be the way round every break. A
     * streak still waiting for its first completion goes for free. The series
     * cannot be ended.
     */
    suspend fun end(id: String) {
        val streak = dao.findById(id) ?: return
        if (streak.kind == StreakKind.SERIES || !streak.isRunning) return
        if (!streak.isWaiting) {
            pointsRepository.record(-STREAK_BREAK_PENALTY, PointsReason.STREAK_BROKEN, note = "${streak.label} beendet")
        }
        val now = clock.now()
        dao.upsert(streak.copy(endedAt = now, updatedAt = now))
    }

    /** The Tagesabschluss-Serie's row, made the first time an evening needs it. */
    suspend fun series(): HabitStreak {
        dao.running().firstOrNull { it.kind == StreakKind.SERIES }?.let { return it }
        val now = clock.now()
        val row = HabitStreak(kind = StreakKind.SERIES, label = SERIES_STREAK_LABEL, startedAt = now, updatedAt = now)
        dao.upsert(row)
        return row
    }

    /**
     * The Streaks switch has just been turned on: the series forgets the last
     * evening it counted, so the next one takes the run as it stands instead of
     * charging for a gap from while the feature was off.
     */
    suspend fun resumeSeries() {
        val series = dao.running().firstOrNull { it.kind == StreakKind.SERIES } ?: return
        dao.upsert(series.copy(lastCountedOn = null, updatedAt = clock.now()))
    }

    /**
     * Writes the evening of [date]. A broken streak is ended, unless its id is
     * in [restart] — then it stays, waiting for its next completion. The series
     * is never ended; broken, it is already counting again.
     */
    suspend fun apply(outcomes: List<StreakOutcome>, restart: Set<String>, date: LocalDate) {
        val now = clock.now()
        dao.upsertAll(
            outcomes.map { outcome ->
                val counted = outcome.streak.copy(length = outcome.after, lastCountedOn = date, updatedAt = now)
                when {
                    outcome.verdict != StreakVerdict.BROKEN -> counted
                    outcome.streak.kind == StreakKind.SERIES -> counted
                    outcome.streak.id in restart -> counted.copy(length = 0)
                    else -> counted.copy(endedAt = now)
                }
            },
        )
    }
}
