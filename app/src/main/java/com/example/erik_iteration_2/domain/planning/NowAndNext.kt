package com.example.erik_iteration_2.domain.planning

import com.example.erik_iteration_2.data.local.BlockWithItem

/** What the dashboard's "now" box shows on its two pages. */
data class NowAndNext(
    val current: BlockWithItem?,
    val next: BlockWithItem?,
)

/**
 * The block running at [minuteOfDay], and the one after it.
 *
 * Only blocks still **open** count. A task whose hour has not run out but which is
 * already ticked off is done, and saying "you are doing this now" would be wrong;
 * the next thing is the useful answer then. Dropped occurrences are skipped for
 * the same reason.
 */
fun nowAndNext(blocks: List<BlockWithItem>, minuteOfDay: Int): NowAndNext {
    val open = blocks.filter { it.block.isOpen }.sortedBy { it.block.startMinute() }

    val current = open.firstOrNull {
        minuteOfDay >= it.block.startMinute() && minuteOfDay < it.block.endMinute()
    }
    val next = open.firstOrNull { it.block.startMinute() > minuteOfDay }

    return NowAndNext(current = current, next = next)
}

/**
 * The notes on one card, the one about this day first.
 *
 * Order is the whole point: the occurrence note is what changed about today, the
 * item note is the standing remark. Blank notes are dropped rather than shown as
 * empty lines.
 */
fun BlockWithItem.notesInOrder(): List<String> =
    listOfNotNull(block.note, item.note).filter { it.isNotBlank() }
