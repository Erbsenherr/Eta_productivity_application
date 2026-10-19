package com.example.eta.data.repository

import com.example.eta.data.local.SubtaskDao
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.SubtaskCheck
import com.example.eta.domain.model.SubtaskTimer
import com.example.eta.domain.subtask.withStepExtras
import kotlin.time.Duration
import com.example.eta.domain.subtask.SubtaskDraft
import com.example.eta.domain.subtask.matchedTo
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import java.util.UUID

/**
 * The steps inside a task, and which of them one day has ticked off.
 *
 * Both tables live here rather than one each in [ItemRepository] and
 * [PlanRepository], because nothing ever wants the one without the other: a list
 * of steps with no ticks says nothing about today, and a tick without its step has
 * nothing to show.
 */
class SubtaskRepository(
    private val dao: SubtaskDao,
    private val clock: Clock = Clock.System,
) {

    fun observeForItem(itemId: String): Flow<List<Subtask>> = dao.observeForItem(itemId)

    /** Grouped by item, for the screens that draw many cards at once. */
    fun observeByItem(): Flow<Map<String, List<Subtask>>> =
        dao.observeAll().map { rows -> rows.groupBy { it.itemId } }

    suspend fun forItem(itemId: String): List<Subtask> = dao.forItem(itemId)

    /** Every group's steps, once — what the evening reads to find what is left open. */
    suspend fun stepsByItem(): Map<String, List<Subtask>> = dao.all().groupBy { it.itemId }

    /** One day's ticks, by block. */
    suspend fun checkedByBlock(date: LocalDate): Map<String, Set<String>> =
        dao.checksForDate(date)
            .groupBy { it.blockId }
            .mapValues { (_, rows) -> rows.map { it.subtaskId }.toSet() }

    /** The ids ticked off on [date], as one set — the shape every caller asks in. */
    fun observeChecked(date: LocalDate): Flow<Map<String, Set<String>>> =
        dao.observeChecksForDate(date).map { checks ->
            checks.groupBy { it.blockId }.mapValues { (_, rows) -> rows.map { it.subtaskId }.toSet() }
        }

    /** When each step was ticked on [date], by block — the routine's timestamps. */
    fun observeCheckTimes(date: LocalDate): Flow<Map<String, Map<String, Instant>>> =
        dao.observeChecksForDate(date).map { checks ->
            checks.groupBy { it.blockId }
                .mapValues { (_, rows) -> rows.associate { it.subtaskId to it.checkedAt } }
        }

    /**
     * Writes the list the builder came back with.
     *
     * Reconciled rather than replaced: a row the user kept keeps its id, and with
     * it every tick already standing against it on a day being lived. Replacing
     * the lot would silently untick a group the user is halfway through.
     *
     * The order in [drafts] *is* the order — position is the index, so a drag in
     * the builder needs nothing else stored.
     */
    suspend fun save(itemId: String, drafts: List<SubtaskDraft>) {
        val now = clock.now()
        val existing = dao.forItem(itemId).associateBy { it.id }
        // Which row each draft lands on. Not simply its id: the same list is
        // saved onto every weekday of a standing task — see [matchedTo].
        val matched = drafts.matchedTo(existing.values.sortedBy { it.position })
        val kept = matched.filterNotNull().toSet()

        val rows = drafts.mapIndexed { index, draft ->
            val row = matched[index]?.let { existing[it] }
            val saved = row?.copy(
                position = index,
                name = draft.name.trim(),
                note = draft.note?.trim()?.takeIf { it.isNotBlank() },
                updatedAt = now,
            ) ?: Subtask(
                // Always a fresh id: a draft's own belongs to another card's row.
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                position = index,
                name = draft.name.trim(),
                note = draft.note?.trim()?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
            )
            // Each weekday's row keeps where its growing and counting have got to.
            draft.extras?.let { saved.withStepExtras(it) } ?: saved
        }

        val gone = existing.values.filterNot { it.id in kept }
        if (gone.isNotEmpty()) dao.delete(gone)
        if (rows.isNotEmpty()) dao.upsert(rows)
    }

    /** The same steps under a new card — what the evening's remainder group needs. */
    suspend fun copy(subtasks: List<Subtask>, toItemId: String) {
        save(toItemId, subtasks.sortedBy { it.position }.map { SubtaskDraft(name = it.name, note = it.note) })
    }

    /** Rows written as they are — what growing and counting move (step 49). */
    suspend fun update(rows: List<Subtask>) {
        if (rows.isNotEmpty()) dao.upsert(rows)
    }

    // --- the timer of a step (step 49) ---------------------------------------

    /** One day's step timers, by block and then by step. */
    fun observeTimers(date: LocalDate): Flow<Map<String, Map<String, SubtaskTimer>>> =
        dao.observeTimersForDate(date).map { rows ->
            rows.groupBy { it.blockId }.mapValues { (_, list) -> list.associateBy { it.subtaskId } }
        }

    suspend fun timersBetween(from: LocalDate, to: LocalDate): List<SubtaskTimer> = dao.timersBetween(from, to)

    suspend fun findTimer(blockId: String, subtaskId: String): SubtaskTimer? = dao.findTimer(blockId, subtaskId)

    /** "Start": the step's whole time, counted from now. */
    suspend fun startTimer(blockId: String, subtaskId: String, goal: Duration) {
        dao.upsertTimer(SubtaskTimer(blockId, subtaskId, goal = goal, runningSince = clock.now()))
    }

    suspend fun pauseTimer(blockId: String, subtaskId: String) {
        val timer = dao.findTimer(blockId, subtaskId) ?: return
        val since = timer.runningSince ?: return
        val worked = (timer.worked + (clock.now() - since)).coerceIn(Duration.ZERO, timer.goal)
        dao.upsertTimer(timer.copy(worked = worked, runningSince = null))
    }

    suspend fun resumeTimer(blockId: String, subtaskId: String) {
        val timer = dao.findTimer(blockId, subtaskId) ?: return
        if (timer.runningSince != null) return
        dao.upsertTimer(timer.copy(runningSince = clock.now()))
    }

    /** "Ende": the count is kept and the step ticked — the routine moves on. */
    suspend fun endTimer(blockId: String, subtaskId: String) {
        pauseTimer(blockId, subtaskId)
        setChecked(blockId, subtaskId, checked = true)
    }

    suspend fun setChecked(blockId: String, subtaskId: String, checked: Boolean) {
        if (checked) {
            dao.check(SubtaskCheck(blockId = blockId, subtaskId = subtaskId, checkedAt = clock.now()))
        } else {
            dao.uncheck(blockId, subtaskId)
            // A tick taken back takes its sitting with it, as for a task.
            dao.deleteTimer(blockId, subtaskId)
        }
    }
}
