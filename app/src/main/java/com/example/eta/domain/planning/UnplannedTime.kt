package com.example.eta.domain.planning

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.reevaluation.FREE_UNPLANNED_HOURS
import com.example.eta.domain.reevaluation.plannedMinutes

/**
 * How the day planner colours the hours a day still leaves empty (step 52).
 *
 * [OVER] past the [FREE_UNPLANNED_HOURS] the evening forgives, [WITHIN] up to
 * them, and [RELAXED] on a weekend day, where nothing has to be planned —
 * unless the user said in Flexibilität that the weekend counts as well.
 */
enum class UnplannedTone { OVER, WITHIN, RELAXED }

/**
 * The waking minutes of a day no block covers: the day, less the night
 * ([sleep], the stretches the planner shades), less every block that still
 * stands or was done, overlaps counted once.
 *
 * The evening's arithmetic, asked in advance: a block called off covers
 * nothing, and a finished one keeps covering its hours.
 */
fun unplannedMinutes(blocks: List<BlockWithItem>, sleep: List<IntRange>): Int {
    val asleep = sleep.sumOf { it.last - it.first + 1 }.coerceIn(0, MINUTES_PER_DAY)
    val covered = plannedMinutes(blocks.filter { it.occupiesTime() })
    return (MINUTES_PER_DAY - asleep - covered).coerceAtLeast(0)
}

/** Which colour [minutes] of unplanned time wears; exactly the free allowance is still within it. */
fun unplannedTone(minutes: Int, weekend: Boolean, weekendPlanning: Boolean): UnplannedTone = when {
    weekend && !weekendPlanning -> UnplannedTone.RELAXED
    minutes > FREE_UNPLANNED_HOURS * 60 -> UnplannedTone.OVER
    else -> UnplannedTone.WITHIN
}
