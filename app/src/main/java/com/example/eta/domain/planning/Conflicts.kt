package com.example.eta.domain.planning

import com.example.eta.data.local.BlockWithItem
import kotlinx.datetime.LocalDate

/**
 * Two blocks of one day that want the same minutes.
 *
 * Compared as containers, like every other question about time in this app: a
 * task with a quarter of an hour of travel in front of it holds that quarter of
 * an hour, and something planned into it is planned into a journey already under
 * way.
 *
 * The pair is ordered by when it starts, so a row reads in the order the day
 * runs. [key] is what a dismissal is stored under — the two block ids, sorted,
 * so the same collision has one name whichever way round it is found.
 */
data class BlockConflict(
    val earlier: BlockWithItem,
    val later: BlockWithItem,
) {
    val key: String get() = conflictKey(earlier.block.id, later.block.id)

    /** Both blocks are of one day, by construction: that is the day. */
    val date: LocalDate get() = earlier.block.date
}

/**
 * The name of one collision.
 *
 * Block ids, which makes a dismissal last exactly one day without a word about
 * dates: one item has one block per day, so tomorrow's clash between the same
 * two tasks is a different pair of ids and asks again. That is the rule the user
 * chose, and it falls out of the identity rather than needing to be enforced.
 */
fun conflictKey(a: String, b: String): String =
    if (a <= b) "$a|$b" else "$b|$a"

/**
 * Every collision on a day, earliest first.
 *
 * A cancelled block holds no hours and a completed one has released its stretch,
 * so neither collides with anything — the same rule placement itself obeys,
 * through [claimsItsSlot]. Everything still standing is compared with everything
 * else exactly once.
 */
fun conflictsOf(blocks: List<BlockWithItem>): List<BlockConflict> {
    val standing = blocks
        .filter { it.claimsItsSlot() }
        .sortedBy { it.block.containerStartMinute() }
    if (standing.size < 2) return emptyList()

    val conflicts = mutableListOf<BlockConflict>()
    standing.forEachIndexed { index, first ->
        standing.drop(index + 1).forEach { second ->
            if (first.block.overlaps(second.block)) conflicts += BlockConflict(first, second)
        }
    }
    return conflicts
}
