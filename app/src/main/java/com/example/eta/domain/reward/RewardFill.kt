package com.example.eta.domain.reward

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.REWARD_EPSILON
import com.example.eta.domain.model.Reward

/**
 * Points one finished task earned, as far as the Belohn-o-mat cares.
 *
 * [taskName] is the task's normalized name — what a binding is matched by — and
 * [standing] says whether it was a standing task at all, since only those can be
 * bound.
 */
data class Earning(
    val taskName: String,
    val standing: Boolean,
    val points: Double,
)

/** A reward as the evening sees it: the row, and the task names it is bound to. */
data class RewardTarget(
    val reward: Reward,
    /** Empty means unbound: every finished task counts. */
    val boundNames: Set<String> = emptySet(),
) {
    fun accepts(earning: Earning): Boolean =
        boundNames.isEmpty() || (earning.standing && earning.taskName in boundNames)
}

/** What one evening pours into one reward. */
data class RewardGain(
    val reward: Reward,
    val added: Double,
) {
    val after: Double get() = (reward.progress + added).coerceAtMost(reward.cost)

    /** This evening is the one that earned it in full. */
    val completes: Boolean get() = after >= reward.cost - REWARD_EPSILON
}

/**
 * What the day's finished tasks earned, one entry per block.
 *
 * **Earned before anything is taken off**, as the user put it: each completed
 * block counts with its own yield, and neither the charge for unplanned hours nor
 * a cancellation reduces it. Only what finishing a task earns — contracts, free
 * time given up and corrections by hand are not in here, and neither is a Custom
 * Spend, whose yield is negative: that is points being spent, not earned.
 */
fun earningsOf(blocks: List<BlockWithItem>): List<Earning> =
    blocks
        .filter { it.block.isCompleted }
        .map { entry ->
            Earning(
                taskName = entry.item.normalizedName,
                standing = entry.item.type == ItemType.RECURRING,
                points = yieldOf(entry.item, entry.block),
            )
        }
        .filter { it.points > 0.0 }

/**
 * Pours [earnings] into [targets], which are in list order.
 *
 * **Only position 1 is worked on**: the first reward still filling takes what it
 * accepts, and what it does not accept — a task it is not bound to — goes
 * nowhere. Rewards further down see nothing of the day.
 *
 * **Unless position 1 is earned in full.** Then it stops being position 1, and
 * what is left of the points that filled it runs on into the next reward, under
 * *that* reward's binding. The surplus is taken from every contributing task in
 * proportion, because points have no order within one evening: which task "came
 * last" is not a question the settlement can answer, and an answer that depended
 * on the order of rows would make the same day pay differently twice.
 *
 * Rewards that are full or redeemed are skipped; they are not in the queue.
 */
fun pourInto(targets: List<RewardTarget>, earnings: List<Earning>): List<RewardGain> {
    val gains = mutableListOf<RewardGain>()
    var flowing = earnings.filter { it.points > 0.0 }

    for (target in targets.filter { it.reward.isFilling }) {
        val accepted = flowing.filter(target::accepts)
        val offered = accepted.sumOf { it.points }
        if (offered <= 0.0) break

        val room = target.reward.cost - target.reward.progress
        if (offered < room - REWARD_EPSILON) {
            gains += RewardGain(target.reward, offered)
            break
        }

        gains += RewardGain(target.reward, room)
        val left = (offered - room) / offered
        if (left <= REWARD_EPSILON) break
        flowing = accepted.map { it.copy(points = it.points * left) }
    }
    return gains
}
