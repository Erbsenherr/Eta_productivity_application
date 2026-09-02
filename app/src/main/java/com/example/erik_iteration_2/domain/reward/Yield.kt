package com.example.erik_iteration_2.domain.reward

import com.example.erik_iteration_2.domain.model.BlockOrigin
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.ItemType
import com.example.erik_iteration_2.domain.model.PlannedBlock
import kotlin.math.floor
import kotlin.time.DurationUnit

/** Points an imported Google Calendar event earns per *full* hour. */
const val CALENDAR_POINTS_PER_FULL_HOUR = 0.5

/** Default rates for the second revolver, in points per hour. */
const val CUSTOM_EARN_POINTS_PER_HOUR = 1.5
const val CUSTOM_SPEND_POINTS_PER_HOUR = -5.0

/**
 * Social time neither earns nor costs: the setup already budgets it out of the
 * free hours, so paying for it again would count it twice. `Belohnungssystem.md`
 * gives rates only for the two custom types, so this is an assumption.
 */
const val SOCIAL_POINTS_PER_HOUR = 0.0

/**
 * Points a block is worth. Positive earns, negative spends.
 *
 * The caller decides which blocks count — the daily reevaluation sums this over
 * the blocks that were checked off. Computing it for an unfinished block is
 * meaningful too: that is the preview of what completing it would be worth.
 */
fun yieldOf(item: Item, block: PlannedBlock): Double {
    val hours = block.effectiveDuration.toDouble(DurationUnit.HOURS)
    return when {
        // Imported events pay per full hour, regardless of what the item says.
        block.origin == BlockOrigin.CALENDAR_IMPORT ->
            floor(hours) * CALENDAR_POINTS_PER_FULL_HOUR

        item.type == ItemType.SPEND ->
            hours * (item.pointsPerHour ?: 0.0)

        else ->
            hours * (item.category?.yieldMultiplier ?: 0.0)
    }
}
