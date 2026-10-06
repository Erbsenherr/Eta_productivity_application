package com.example.eta.data.repository

import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.local.ItemDao
import com.example.eta.data.local.PlannedBlockDao
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.subtask.remainderDuration
import com.example.eta.domain.reevaluation.DiscardConsequence
import com.example.eta.domain.reevaluation.consequenceOf
import com.example.eta.domain.reevaluation.returnedToCollection
import kotlin.time.Clock
import kotlinx.datetime.LocalDate

/**
 * Step 1 of the Tägliche Reevaluation: settling what did and did not happen.
 *
 * Spans items and blocks, which is why it sits beside the two repositories rather
 * than inside either of them.
 */
class DayClosingService(
    private val itemDao: ItemDao,
    private val blockDao: PlannedBlockDao,
    private val subtaskRepository: SubtaskRepository,
    private val clock: Clock = Clock.System,
) {

    /** Everything on [date] that was neither completed nor already dropped. */
    suspend fun openBlocks(date: LocalDate): List<BlockWithItem> =
        blockDao.findOpenForDateWithItems(date)

    /**
     * Drops an occurrence and applies the consequence for its item. Returns what
     * happened so the caller knows whether to offer a catch-up.
     */
    suspend fun discard(entry: BlockWithItem): DiscardConsequence {
        val now = clock.now()
        blockDao.upsert(entry.block.copy(discardedAt = now, updatedAt = now))

        val consequence = consequenceOf(entry.item)
        if (consequence == DiscardConsequence.RETURN_TO_COLLECTION) {
            itemDao.upsert(entry.item.returnedToCollection(now))
        }
        return consequence
    }

    /**
     * Creates the "Nachholen von …" ToDo for a dropped recurring occurrence.
     *
     * Deliberately skips the Sperrliste check: this follows from a commitment the
     * user still holds, not from a fresh idea being hoarded. Idempotent — a block
     * that already spawned a catch-up returns that one instead of a second.
     */
    suspend fun makeUp(entry: BlockWithItem): Item {
        entry.block.makeUpItemId?.let { existingId ->
            itemDao.findById(existingId)?.let { return it }
        }

        val now = clock.now()
        val makeUp = Item.newMakeUpTodo(entry.item, now)
        itemDao.upsert(makeUp)
        blockDao.upsert(entry.block.copy(makeUpItemId = makeUp.id, updatedAt = now))
        return makeUp
    }

    /**
     * Carries a group's unfinished steps over into a new group in the week list.
     *
     * Shares `makeUpItemId` with [makeUp], and that is the point rather than thrift:
     * for a recurring task this **replaces** the catch-up, so the two can never both
     * stand against one block, and the same column makes it idempotent — a second
     * tap returns the card that already exists.
     *
     * [open] are the steps still unticked and [total] how many there were, which is
     * what the length is a share of. The Sperrliste is not consulted, for the reason
     * a catch-up does not consult it either: this follows from work already begun.
     */
    suspend fun carryOverSubtasks(
        entry: BlockWithItem,
        open: List<Subtask>,
        total: Int,
        today: LocalDate,
    ): Item {
        entry.block.makeUpItemId?.let { existingId ->
            itemDao.findById(existingId)?.let { return it }
        }

        val now = clock.now()
        val group = Item.remainderGroup(
            of = entry.item,
            duration = remainderDuration(entry.block.effectiveDuration, open.size, total),
            today = today,
            now = now,
        )
        itemDao.upsert(group)
        subtaskRepository.copy(open, group.id)
        blockDao.upsert(entry.block.copy(makeUpItemId = group.id, updatedAt = now))
        return group
    }
}
