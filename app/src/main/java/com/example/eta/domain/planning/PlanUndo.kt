package com.example.eta.domain.planning

import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import kotlin.time.Instant

/**
 * One day of the planner as it stood before a change — what "Rückgängig" goes
 * back to (step 53).
 *
 * The blocks of the day, every state included, and the cards a change on it can
 * touch: the ones behind those blocks, and the week list and the Sammelliste,
 * where a card handed back, called off or copied lands. [takenAt] tells a card
 * made by the change from one that was there.
 */
data class PlanSnapshot(
    val blocks: List<PlannedBlock>,
    val items: List<Item>,
    val takenAt: Instant,
) {
    /** Whether [other] holds the same rows — a change that changed nothing is not one to undo. */
    fun sameRowsAs(other: PlanSnapshot): Boolean =
        blocks.toSet() == other.blocks.toSet() && items.toSet() == other.items.toSet()
}

/** The writes that take the day from what it is now back to a snapshot. */
data class PlanRestore(
    /** Blocks the change laid down: gone first, so the old ones find their place free. */
    val removeBlocks: List<PlannedBlock>,
    /** Cards changed or deleted since, back as they were — before their blocks. */
    val restoreItems: List<Item>,
    /** Blocks moved, cut, called off or taken away since, back as they were. */
    val restoreBlocks: List<PlannedBlock>,
    /** Cards the change made: a ToDo or a break on the day, a copy in the week. */
    val deleteItems: List<Item>,
)

/**
 * Works out [PlanRestore] from the snapshot and the day as it stands.
 *
 * By rows, not by remembering what each action did: nine actions reach the day,
 * and an undo that had to know each of them would be nine undos that drift.
 * A card that is new since the snapshot is deleted only if it was **made** after
 * it — one merely brought into view, such as a card that has come into the week
 * list from elsewhere, is not the change's to take away.
 */
fun planRestore(before: PlanSnapshot, now: PlanSnapshot): PlanRestore {
    val blocksBefore = before.blocks.associateBy { it.id }
    val blocksNow = now.blocks.associateBy { it.id }
    val itemsBefore = before.items.associateBy { it.id }
    val itemsNow = now.items.associateBy { it.id }
    return PlanRestore(
        removeBlocks = now.blocks.filter { it.id !in blocksBefore },
        restoreItems = before.items.filter { itemsNow[it.id] != it },
        restoreBlocks = before.blocks.filter { blocksNow[it.id] != it },
        deleteItems = now.items.filter { it.id !in itemsBefore && it.createdAt >= before.takenAt },
    )
}

/** How many changes "Rückgängig" can go back. */
const val PLAN_UNDO_DEPTH = 20
