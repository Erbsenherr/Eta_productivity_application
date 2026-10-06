package com.example.eta.domain.planning

import com.example.eta.data.local.BlockWithItem

/** Which part of a block's container a minute falls in. */
enum class BlockPhase { TRAVEL, TASK, RETURN, BREAK }

/** A block together with the part of it that is meant. */
data class NowEntry(
    val entry: BlockWithItem,
    val phase: BlockPhase,
)

/** What the dashboard's "now" box shows on its two pages. */
data class NowAndNext(
    val current: NowEntry?,
    val next: NowEntry?,
)

/** The phase [minuteOfDay] falls in, or null when it is outside the container. */
fun BlockWithItem.phaseAt(minuteOfDay: Int): BlockPhase? = when {
    minuteOfDay < block.containerStartMinute() -> null
    minuteOfDay < block.startMinute() -> BlockPhase.TRAVEL
    minuteOfDay < block.endMinute() -> BlockPhase.TASK
    minuteOfDay < block.breakStartMinute() -> BlockPhase.RETURN
    minuteOfDay < block.containerEndMinute() -> BlockPhase.BREAK
    else -> null
}

/**
 * The block running at [minuteOfDay], and the one after it.
 *
 * Measured over the **container**, not the task. An appointment at 17:15 with a
 * quarter of an hour of travel claims your attention at 17:00, and a box that
 * said "next: 17:15" would be telling you about a time by which you should
 * already be there. The phase is what the box appends, so the answer reads
 * "Zahnarzt · Anfahrt" rather than pretending the journey is the appointment.
 *
 * The asymmetry the user asked for follows from the same span: the break is at
 * the far end, so it can be something you are in the middle of but never the next
 * thing to prepare for.
 *
 * Only blocks still **open** count. A task whose hour has not run out but which is
 * already ticked off is done, and saying "you are doing this now" would be wrong;
 * the next thing is the useful answer then. Cancelled occurrences are gone from
 * the day altogether.
 */
fun nowAndNext(blocks: List<BlockWithItem>, minuteOfDay: Int): NowAndNext {
    val open = blocks.filter { it.block.isOpen }.sortedBy { it.block.containerStartMinute() }

    val current = open.firstNotNullOfOrNull { entry ->
        entry.phaseAt(minuteOfDay)?.let { NowEntry(entry, it) }
    }
    val next = open
        .firstOrNull { it.block.containerStartMinute() > minuteOfDay }
        ?.let { NowEntry(it, it.phaseAt(it.block.containerStartMinute()) ?: BlockPhase.TASK) }

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
