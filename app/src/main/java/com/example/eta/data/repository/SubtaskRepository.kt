package com.example.eta.data.repository

import com.example.eta.data.local.SubtaskDao
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.SubtaskCheck
import com.example.eta.domain.subtask.SubtaskDraft
import kotlin.time.Clock
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
        val kept = drafts.mapNotNull { it.id }.toSet()

        val rows = drafts.mapIndexed { index, draft ->
            val row = draft.id?.let { existing[it] }
            row?.copy(
                position = index,
                name = draft.name.trim(),
                note = draft.note?.trim()?.takeIf { it.isNotBlank() },
                updatedAt = now,
            ) ?: Subtask(
                id = draft.id ?: UUID.randomUUID().toString(),
                itemId = itemId,
                position = index,
                name = draft.name.trim(),
                note = draft.note?.trim()?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now,
            )
        }

        val gone = existing.values.filterNot { it.id in kept }
        if (gone.isNotEmpty()) dao.delete(gone)
        if (rows.isNotEmpty()) dao.upsert(rows)
    }

    /** The same steps under a new card — what the evening's remainder group needs. */
    suspend fun copy(subtasks: List<Subtask>, toItemId: String) {
        save(toItemId, subtasks.sortedBy { it.position }.map { SubtaskDraft(name = it.name, note = it.note) })
    }

    suspend fun setChecked(blockId: String, subtaskId: String, checked: Boolean) {
        if (checked) {
            dao.check(SubtaskCheck(blockId = blockId, subtaskId = subtaskId, checkedAt = clock.now()))
        } else {
            dao.uncheck(blockId, subtaskId)
        }
    }
}
