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
    /** Empty means unbound: every finished task that is bound nowhere counts. */
    val boundNames: Set<String> = emptySet(),
) {
    /** Bound rewards collect from their own tasks; unbound ones queue for the rest. */
    val isBound: Boolean get() = boundNames.isNotEmpty()
}

/** What a reward is doing, which is what its card says. */
enum class RewardRole {
    /** The first unbound reward still filling: where every unbound task's points go. */
    FILLING,

    /** Bound and still filling: collects from its own tasks, wherever it stands. */
    COLLECTING,

    /** Unbound and further down: sees nothing of the day until those above are earned. */
    WAITING,

    /** Earned in full, waiting to be redeemed. */
    FULL,
}

/**
 * Which standing task belongs to which reward: its normalized name → the reward.
 *
 * **A task is bound to one reward only.** Should two claim it — rows from before
 * that was the rule — the one further up the list has it. A reward that is full
 * keeps its tasks until it is redeemed, so their points go nowhere meanwhile
 * rather than quietly counting somewhere else; redeemed, it lets them go.
 */
fun claimsOf(targets: List<RewardTarget>): Map<String, Reward> = buildMap {
    targets.filter { !it.reward.isRedeemed }.forEach { target ->
        target.boundNames.forEach { name -> putIfAbsent(name, target.reward) }
    }
}

/** What each reward not yet redeemed is doing, by its id — see [RewardRole]. */
fun rolesOf(targets: List<RewardTarget>): Map<String, RewardRole> {
    val head = targets.firstOrNull { !it.isBound && it.reward.isFilling }?.reward?.id
    return targets.filter { !it.reward.isRedeemed }.associate { target ->
        target.reward.id to when {
            target.reward.isFull -> RewardRole.FULL
            target.isBound -> RewardRole.COLLECTING
            target.reward.id == head -> RewardRole.FILLING
            else -> RewardRole.WAITING
        }
    }
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
 * Two kinds of reward, and they do not share points:
 *
 * **A bound reward collects from its own tasks, wherever it stands in the list.**
 * Position means nothing to it. What its tasks earn beyond what it can still
 * hold goes nowhere — and so does everything they earn while it is full and
 * waiting to be redeemed. A bound task counts for its reward and for no other.
 *
 * **Everything else goes to the first unbound reward still filling**, and to
 * that one alone; unbound rewards further down see nothing of the day. Bound
 * rewards standing above it are simply passed over. Only when that reward is
 * earned in full does what is left run on into the next unbound one — taken from
 * every contributing task in proportion, because points have no order within one
 * evening: which task "came last" is not a question the settlement can answer.
 *
 * The gains come back in list order.
 */
fun pourInto(targets: List<RewardTarget>, earnings: List<Earning>): List<RewardGain> {
    val earned = earnings.filter { it.points > 0.0 }
    val claims = claimsOf(targets)
    val gains = mutableMapOf<String, RewardGain>()

    // Bound: each reward and the tasks that are its own.
    targets.filter { it.isBound && it.reward.isFilling }.forEach { target ->
        val offered = earned
            .filter { it.standing && claims[it.taskName]?.id == target.reward.id }
            .sumOf { it.points }
        if (offered > 0.0) {
            val room = target.reward.cost - target.reward.progress
            gains[target.reward.id] = RewardGain(target.reward, minOf(offered, room))
        }
    }

    // Unbound: what no reward has claimed, down the queue.
    var flowing = earned.filter { !(it.standing && it.taskName in claims) }
    for (target in targets.filter { !it.isBound && it.reward.isFilling }) {
        val offered = flowing.sumOf { it.points }
        if (offered <= 0.0) break

        val room = target.reward.cost - target.reward.progress
        if (offered < room - REWARD_EPSILON) {
            gains[target.reward.id] = RewardGain(target.reward, offered)
            break
        }

        gains[target.reward.id] = RewardGain(target.reward, room)
        val left = (offered - room) / offered
        if (left <= REWARD_EPSILON) break
        flowing = flowing.map { it.copy(points = it.points * left) }
    }

    return targets.mapNotNull { gains[it.reward.id] }
}
