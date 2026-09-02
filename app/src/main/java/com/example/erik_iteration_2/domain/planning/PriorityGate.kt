package com.example.erik_iteration_2.domain.planning

import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Priority

/** What the revolver is currently allowed to offer, and what it is holding back. */
data class PriorityTier(
    val priority: Priority?,
    val items: List<Item>,
    /** Tiers still locked below this one — what a manual unlock would reveal. */
    val lockedBelow: Int,
)

/**
 * The revolver offers **one priority at a time**: the highest still outstanding,
 * and nothing below it until that tier is empty.
 *
 * [skipped] is how many tiers the user has waved past by hand. Without it a tier
 * that cannot be placed — nothing left in the day fits it — would hold the whole
 * revolver shut and hide everything below, which is the one way this rule can go
 * wrong. Skipping is the escape hatch, and it is deliberate rather than automatic:
 * the point of the rule is that the important things are offered first.
 *
 * [Priority] is declared highest-first, so its natural order is the ranking.
 * Items without a priority sort last; they are what a Quick-Add becomes before
 * anyone has said how much it matters.
 */
fun priorityTier(candidates: List<Item>, skipped: Int = 0): PriorityTier {
    if (candidates.isEmpty()) return PriorityTier(null, emptyList(), lockedBelow = 0)

    val tiers = candidates
        .groupBy { it.priority }
        .entries
        .sortedBy { it.key?.ordinal ?: Int.MAX_VALUE }

    val index = skipped.coerceIn(0, tiers.lastIndex)
    val tier = tiers[index]

    return PriorityTier(
        priority = tier.key,
        items = tier.value,
        lockedBelow = tiers.lastIndex - index,
    )
}
