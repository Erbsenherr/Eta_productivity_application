package com.example.erik_iteration_2.data.repository

import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.data.local.ItemDao
import com.example.erik_iteration_2.data.local.PlannedBlockDao
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.reevaluation.DiscardConsequence
import com.example.erik_iteration_2.domain.reevaluation.consequenceOf
import com.example.erik_iteration_2.domain.reevaluation.returnedToCollection
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
}
