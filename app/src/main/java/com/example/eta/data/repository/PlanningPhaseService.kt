package com.example.eta.data.repository

import com.example.eta.domain.planning.PLANNING_RETRY
import com.example.eta.domain.planning.PlanningPhase
import com.example.eta.domain.planning.nextPlanning
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * When a planning phase is next due, and whether it still needs doing.
 *
 * "Done" is read off what the phase actually leaves behind rather than a flag of
 * its own: the daily phase ends by confirming tomorrow's plan, the weekly one by
 * booking the devaluation. A separate marker could disagree with the thing it
 * claims to describe.
 */
class PlanningPhaseService(
    private val setupRepository: SetupRepository,
    private val planRepository: PlanRepository,
    private val weekPlanningService: WeekPlanningService,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    suspend fun isCompleted(phase: PlanningPhase): Boolean = when (phase) {
        // Both halves, not just the second. Planning ahead is encouraged, and it
        // must not be able to silence the alarm before the day is settled.
        PlanningPhase.DAILY -> {
            val today = clock.todayIn(timeZone)
            val tomorrow = today.plus(DatePeriod(days = 1))
            planRepository.findDayPlan(today)?.isSettled == true &&
                planRepository.findDayPlan(tomorrow)?.isConfirmed == true
        }

        PlanningPhase.WEEKLY -> weekPlanningService.inflationPreview().alreadyApplied
    }

    /** Null until the questionnaire has been answered — there is nothing to ring for. */
    suspend fun nextDue(phase: PlanningPhase): Instant? {
        val setup = setupRepository.find() ?: return null
        val now = clock.now().toLocalDateTime(timeZone)
        return nextPlanning(setup, phase, now).toInstant(timeZone)
    }

    /** When to ask again while the phase is still open. */
    fun retryAt(): Instant = clock.now() + PLANNING_RETRY
}
