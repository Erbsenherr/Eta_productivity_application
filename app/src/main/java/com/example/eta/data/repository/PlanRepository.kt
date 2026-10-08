package com.example.eta.data.repository

import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.local.DayPlanDao
import com.example.eta.data.local.PlannedBlockDao
import com.example.eta.domain.model.DayPlan
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.recurrence.expandRecurring
import com.example.eta.domain.vacation.VacationPlan
import kotlin.time.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

class PlanRepository(
    private val blockDao: PlannedBlockDao,
    private val dayPlanDao: DayPlanDao,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    fun observeDay(date: LocalDate): Flow<List<BlockWithItem>> =
        blockDao.observeForDateWithItems(date)

    /** One block by its identity, for callers that must not work from a stale copy. */
    suspend fun findBlock(id: String): PlannedBlock? = blockDao.findById(id)

    /** One day, read once. What the alarms need, which cannot hold a flow open. */
    suspend fun findDay(date: LocalDate): List<BlockWithItem> =
        blockDao.findForDateWithItems(date)

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<BlockWithItem>> =
        blockDao.observeForRangeWithItems(from, to)

    /** A stretch of days, read once — what the maintenance passes work over. */
    suspend fun findRange(from: LocalDate, to: LocalDate): List<BlockWithItem> =
        blockDao.findForRangeWithItems(from, to)

    /** What was ticked off on [date], for the growth increment and the harvest. */
    suspend fun completedOn(date: LocalDate): List<BlockWithItem> =
        blockDao.findCompletedForDateWithItems(date)

    fun observeDayPlan(date: LocalDate): Flow<DayPlan?> = dayPlanDao.observe(date)

    suspend fun findDayPlan(date: LocalDate): DayPlan? = dayPlanDao.find(date)

    /**
     * The last day an edit to a standing task leaves alone: today, or tomorrow
     * once tomorrow's plan is confirmed.
     *
     * Today is being lived, and a confirmed tomorrow is a promise already made;
     * rewriting either from a definition would change a plan behind the user's
     * back. Everything after them is still only laid down ahead of time.
     *
     * Here rather than on one of the services, because three of them now ask it —
     * the standing-schedule editor, the growth increment and the dynamic
     * placement pass — and all three have to mean the same day.
     */
    suspend fun lastKeptDay(): LocalDate {
        val today = clock.todayIn(timeZone)
        val tomorrow = today.plus(DatePeriod(days = 1))
        return if (findDayPlan(tomorrow)?.isConfirmed == true) tomorrow else today
    }

    /** The first day still free to be laid down again — the day after [lastKeptDay]. */
    suspend fun firstOpenDay(): LocalDate = lastKeptDay().plus(DatePeriod(days = 1))

    /** Every settled day, for the streak. */
    fun observeSettledDates(): Flow<List<LocalDate>> = dayPlanDao.observeSettledDates()

    /**
     * Imported appointments still ahead of [after], soonest first.
     *
     * The Terminliste: what the calendar has put on days that have not come yet.
     * Everything else on the Listen-Tab is about today, tomorrow or a backlog, so
     * a fixture three weeks out had nowhere to be seen at all.
     */
    fun observeUpcomingAppointments(after: LocalDate): Flow<List<BlockWithItem>> =
        blockDao.observeUpcomingImportsWithItems(after)

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

    /**
     * Calls an occurrence off in advance, without removing it.
     *
     * Deleting would not hold: `expandRecurring` skips only the item/date pairs
     * currently materialized, so a deleted occurrence of a day still ahead comes
     * back on the next top-up. The row has to stay for the schedule to know the
     * day was already decided — and a discarded block is also one the evening
     * reevaluation no longer asks about.
     *
     * [forceMajeure] excuses it in the same write, for a cancellation that was
     * nobody's doing and is said to be so on the spot — see [excuse]. One write
     * rather than two calls, each of which would save its own copy of the row
     * and the second undo the first.
     */
    suspend fun discard(block: PlannedBlock, forceMajeure: String? = null) {
        val now = clock.now()
        blockDao.upsert(
            block.copy(
                discardedAt = now,
                forceMajeure = forceMajeure ?: block.forceMajeure,
                updatedAt = now,
            ),
        )
    }

    /** Taking a calling-off back. The mirror of [discard], for a change of mind. */
    suspend fun undiscard(block: PlannedBlock) {
        blockDao.upsert(
            block.copy(discardedAt = null, forceMajeure = null, updatedAt = clock.now()),
        )
    }

    /**
     * Excuses a cancellation, so the evening does not charge for it.
     *
     * Stored on the block rather than held in the reevaluation, so that leaving
     * the screen and coming back cannot lose it — and so the reason stays as the
     * record of why that day reads the way it does.
     */
    suspend fun excuse(block: PlannedBlock, reason: String) {
        blockDao.upsert(block.copy(forceMajeure = reason, updatedAt = clock.now()))
    }

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
