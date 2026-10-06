package com.example.eta.domain.reward

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import kotlin.math.floor
import kotlin.time.Duration
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
            yieldOf(item.category, block.effectiveDuration)
    }
}

/**
 * What a stretch of [duration] in [category] is worth, before there is a block.
 *
 * The same arithmetic the ordinary case of [yieldOf] does, pulled out so a screen
 * can show what finishing a card would pay while the user is still choosing the
 * answers it pays by. A card with no category pays nothing, which is the rule the
 * frame of the day already runs on.
 */
fun yieldOf(category: Category?, duration: Duration): Double =
    duration.toDouble(DurationUnit.HOURS) * (category?.yieldMultiplier ?: 0.0)

/**
 * What a day's plan is worth if everything still standing on it gets done.
 *
 * The forecast, as opposed to the harvest: the harvest counts only what was
 * ticked off, so placing a Custom Earn or a Custom Spend — or changing its rate —
 * moved nothing until the block was checked, and the one number that should have
 * answered "what will this hour cost me" stayed put.
 *
 * A called-off block is left out, because it is not going to happen; what it
 * costs instead is the evening's cancellation line, not a yield. Completed blocks
 * count with the duration they really took.
 */
fun plannedYield(blocks: List<BlockWithItem>): Double =
    blocks
        .filter { !it.block.isDiscarded }
        .sumOf { yieldOf(it.item, it.block) }
