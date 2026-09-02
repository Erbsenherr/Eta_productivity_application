package com.example.erik_iteration_2.data.repository

import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.data.local.DayPlanDao
import com.example.erik_iteration_2.data.local.PlannedBlockDao
import com.example.erik_iteration_2.domain.model.DayPlan
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.recurrence.expandRecurring
import com.example.erik_iteration_2.domain.vacation.VacationPlan
import com.example.erik_iteration_2.domain.reward.yieldOf
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

class PlanRepository(
    private val blockDao: PlannedBlockDao,
    private val dayPlanDao: DayPlanDao,
    private val clock: Clock = Clock.System,
) {

    fun observeDay(date: LocalDate): Flow<List<BlockWithItem>> =
        blockDao.observeForDateWithItems(date)

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<BlockWithItem>> =
        blockDao.observeForRangeWithItems(from, to)

    fun observeDayPlan(date: LocalDate): Flow<DayPlan?> = dayPlanDao.observe(date)

    suspend fun findDayPlan(date: LocalDate): DayPlan? = dayPlanDao.find(date)

    /** Every settled day, for the streak. */
    fun observeSettledDates(): Flow<List<LocalDate>> = dayPlanDao.observeSettledDates()

    /** The Erfolgsliste — every completed occurrence, newest first. */
    fun observeErfolgsliste(): Flow<List<BlockWithItem>> = blockDao.observeCompletedWithItems()

    /**
     * Materializes the blocks the given recurring definitions owe for the range.
     * Idempotent: existing occurrences are left alone. Returns how many were added.
     */
    suspend fun materializeRecurring(
        definitions: List<Item>,
        from: LocalDate,
        to: LocalDate,
        vacations: List<VacationPlan> = emptyList(),
    ): Int {
        val existing = blockDao.findExistingSlots(from, to)
            .map { it.itemId to it.date }
            .toSet()
        val blocks = expandRecurring(definitions, from, to, existing, clock.now(), vacations)
        if (blocks.isEmpty()) return 0
        // The read above can be stale — two runs overlapping both see "nothing
        // there". The unique index is what actually keeps the day from doubling;
        // this only avoids turning that into an exception.
        return blockDao.insertMissing(blocks).count { it != -1L }
    }

    /** Takes the still-open occurrences of retired definitions off the calendar. */
    suspend fun clearUpcoming(itemIds: List<String>, from: LocalDate): Int =
        if (itemIds.isEmpty()) 0 else blockDao.clearUpcomingFor(itemIds, from)

    suspend fun addBlock(block: PlannedBlock) = blockDao.upsert(block)

    suspend fun addBlocks(blocks: List<PlannedBlock>) = blockDao.upsertAll(blocks)

    suspend fun removeBlock(block: PlannedBlock) = blockDao.delete(block)

    /**
     * Checks a block off. [actualDuration] is the correction the user may supply
     * on the dashboard or during the evening reevaluation.
     */
    suspend fun complete(block: PlannedBlock, actualDuration: Duration? = null) {
        val now = clock.now()
        blockDao.upsert(
            block.copy(
                completedAt = now,
                actualDuration = actualDuration ?: block.actualDuration,
                updatedAt = now,
            ),
        )
    }

    suspend fun reopen(block: PlannedBlock) {
        blockDao.upsert(block.copy(completedAt = null, updatedAt = clock.now()))
    }

    /** Total points earned by everything checked off on [date]. */
    suspend fun harvestFor(date: LocalDate): Double =
        blockDao.findCompletedForDateWithItems(date)
            .sumOf { yieldOf(it.item, it.block) }

    /**
     * Records that the evening settled [date].
     *
     * Kept next to the confirmation because both describe the state of one day —
     * and read together they are what tells the alarm the phase is really over.
     */
    suspend fun markSettled(date: LocalDate) {
        val existing = dayPlanDao.find(date)
        dayPlanDao.upsert(
            (existing ?: DayPlan(date = date)).copy(settledAt = clock.now()),
        )
    }

    /** Ends the daily planning phase; the day then shows as the read-only "Liste für Morgen". */
    suspend fun confirmDayPlan(date: LocalDate) {
        val existing = dayPlanDao.find(date)
        dayPlanDao.upsert(
            (existing ?: DayPlan(date = date)).copy(confirmedAt = clock.now()),
        )
    }

    /** Long-press on the "Liste für Morgen" reopens editing and brings the revolver back. */
    suspend fun reopenDayPlan(date: LocalDate) {
        val existing = dayPlanDao.find(date) ?: return
        dayPlanDao.upsert(existing.copy(confirmedAt = null))
    }
}
