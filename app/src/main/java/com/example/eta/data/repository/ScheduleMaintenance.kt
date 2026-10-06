package com.example.eta.data.repository

import com.example.eta.data.local.ItemDao
import com.example.eta.domain.growth.placeDynamicGrowth
import com.example.eta.domain.planning.minuteToLocalTime
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

/** How far ahead the standing schedule is kept materialized. */
const val SCHEDULE_HORIZON_DAYS = 21

/**
 * Keeps the standing schedule laid down ahead of today.
 *
 * Without this the recurring tasks quietly run out: the setup materialized a
 * fortnight and nothing extended it, so two weeks after answering the
 * questionnaire the day planner, the free-hour maths and the "now" box would all
 * have gone empty with no error to explain it.
 *
 * Cheap to call often — `expandRecurring` skips what already exists — so it runs
 * whenever the app starts and at the top of each planning phase, which is where
 * the concept always said expansion belonged.
 */
class ScheduleMaintenance(
    private val itemDao: ItemDao,
    private val planRepository: PlanRepository,
    private val vacationRepository: VacationRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    /** Materializes from today to the horizon. Returns how many blocks were added. */
    suspend fun topUp(): Int = topUpUntil(clock.todayIn(timeZone).plus(DatePeriod(days = SCHEDULE_HORIZON_DAYS)))

    /** Materializes at least as far as [until], for a range that reaches further. */
    suspend fun topUpUntil(until: LocalDate): Int {
        val today = clock.todayIn(timeZone)
        val horizon = maxOf(until, today.plus(DatePeriod(days = SCHEDULE_HORIZON_DAYS)))
        val added = planRepository.materializeRecurring(
            definitions = itemDao.findRecurringDefinitions(),
            from = today,
            to = horizon,
            vacations = vacationRepository.plansFrom(today),
        )
        // A dynamic growth task's hour is not a property of the task but of the
        // day it lands in, so a freshly materialized occurrence has no hour worth
        // keeping until this has run over it.
        relayDynamicGrowth(horizon)
        return added
    }

    /**
     * Puts every dynamic growth task of every day still open where the day says
     * it belongs.
     *
     * Recomputed rather than fixed at materialization, which is what the user
     * asked for: the whole point of dynamic placement is a gapless chain, and a
     * chain whose links were nailed down when they were created would come apart
     * the first time one of them grew. Cheap to call often — nothing is written
     * unless a block actually moved.
     *
     * **Only days that are still open.** Today is being lived and a confirmed
     * tomorrow is a promise already made; the same line every other edit to a
     * standing task respects. Returns how many blocks moved.
     */
    suspend fun relayDynamicGrowth(until: LocalDate? = null): Int {
        val from = planRepository.firstOpenDay()
        val to = maxOf(
            until ?: from,
            clock.todayIn(timeZone).plus(DatePeriod(days = SCHEDULE_HORIZON_DAYS)),
        )
        if (to < from) return 0

        val now = clock.now()
        val moved = planRepository.findRange(from, to)
            .groupBy { it.block.date }
            .values
            .flatMap { day -> placeDynamicGrowth(day) }
            .filter { it.moved }
            .map { move ->
                move.block.copy(start = minuteToLocalTime(move.toMinute), updatedAt = now)
            }
        if (moved.isEmpty()) return 0
        planRepository.addBlocks(moved)
        return moved.size
    }

    /**
     * Takes the still-open occurrences of [itemIds] off the days that are still
     * open and lays them down again from the definitions as they now stand.
     *
     * The rule every edit to a standing task follows: today, and tomorrow once
     * its plan is confirmed, keep what they have.
     */
    suspend fun relayOccurrences(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        planRepository.clearUpcoming(itemIds, planRepository.firstOpenDay())
        topUp()
    }
}
