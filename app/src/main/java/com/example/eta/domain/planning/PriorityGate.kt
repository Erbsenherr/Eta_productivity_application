package com.example.eta.domain.planning

import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Priority

/** What the revolver is currently allowed to offer, and what it is holding back. */
data class PriorityTier(
    val priority: Priority?,
    val items: List<Item>,
    /** Tiers still locked below this one — what a manual unlock would reveal. */
    val lockedBelow: Int,
    /**
     * Tiers above this one that were passed **by themselves**, nothing in them
     * fitting the day any more. Said on the screen, so a tier that is suddenly
     * not on offer does not read as one that went missing.
     */
    val passedAbove: Int = 0,
)

/**
 * The revolver offers **one priority at a time**: the highest still outstanding,
 * and nothing below it until that tier is empty.
 *
 * The one way this rule can go wrong is a tier that cannot be placed — nothing
 * left in the day fits it — which would hold the whole revolver shut and hide
 * everything below. Two answers to that:
 *
 * - **[fits]** is asked of every card, and a tier in which nothing fits is passed
 *   by itself. That is not a way round the rule: what is passed could not have
 *   been planned anyway. A tier with even one card that fits holds.
 * - **[skipped]** is how many further tiers the user has waved past by hand —
 *   "Überspringen", which since the Flexibilität settings exists only for someone
 *   who switched it on. The caller passes 0 otherwise.
 *
 * When nothing fits anywhere the top tier is offered as it always was: the drop
 * is refused with its own message, and a revolver that showed nothing would only
 * hide why.
 *
 * [Priority] is declared highest-first, so its natural order is the ranking.
 * Items without a priority sort last; they are what a Quick-Add becomes before
 * anyone has said how much it matters.
 */
fun priorityTier(
    candidates: List<Item>,
    skipped: Int = 0,
    fits: (Item) -> Boolean = { true },
): PriorityTier {
    if (candidates.isEmpty()) return PriorityTier(null, emptyList(), lockedBelow = 0)

    val tiers = candidates
        .groupBy { it.priority }
        .entries
        .sortedBy { it.key?.ordinal ?: Int.MAX_VALUE }

    val passed = tiers.indexOfFirst { tier -> tier.value.any(fits) }.coerceAtLeast(0)
    val index = (passed + skipped).coerceIn(0, tiers.lastIndex)
    val tier = tiers[index]

    return PriorityTier(
        priority = tier.key,
        items = tier.value,
        lockedBelow = tiers.lastIndex - index,
        passedAbove = passed,
    )
}
