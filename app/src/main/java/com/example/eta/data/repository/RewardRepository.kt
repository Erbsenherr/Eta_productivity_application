package com.example.eta.data.repository

import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.local.ItemDao
import com.example.eta.data.local.RewardDao
import com.example.eta.domain.model.Reward
import com.example.eta.domain.model.RewardTask
import com.example.eta.domain.reward.RewardGain
import com.example.eta.domain.reward.RewardTarget
import com.example.eta.domain.reward.earningsOf
import com.example.eta.domain.reward.pourInto
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * A reward together with the standing tasks it is bound to, as normalized names.
 * Empty means unbound: every finished task counts.
 */
data class RewardWithTasks(
    val reward: Reward,
    val boundNames: Set<String> = emptySet(),
)

/**
 * What an evening would do to the list: the reward at position 1 — the first one
 * still filling, null when there is none — and what would be poured where.
 */
data class RewardOutlook(
    val head: Reward?,
    /** Whether [head] only counts the tasks it is bound to. */
    val headBound: Boolean,
    val gains: List<RewardGain>,
)

/**
 * The Belohn-o-mat's rows, and the one thing that fills them.
 *
 * [pour] is called from `ReevaluationService` alone — at the settlement and for a
 * completion carried to the ledger late — so a reward fills exactly when HARVEST
 * is written, and by the same blocks.
 */
class RewardRepository(
    private val rewardDao: RewardDao,
    private val itemDao: ItemDao,
    private val clock: Clock = Clock.System,
) {

    /**
     * The definitions ride along only as a trigger: renaming or ending a bound
     * task changes what a binding means without touching either reward table.
     */
    fun observe(): Flow<List<RewardWithTasks>> = combine(
        rewardDao.observeAll(),
        rewardDao.observeTasks(),
        itemDao.observeRecurringDefinitions(),
    ) { rewards, tasks, definitions ->
        val live = definitions.mapTo(mutableSetOf()) { it.normalizedName }
        val byReward = tasks.groupBy({ it.rewardId }, { it.itemId })
        rewards.map { RewardWithTasks(it, boundNames(byReward[it.id].orEmpty(), live)) }
    }

    /**
     * What a binding to [itemIds] means: the names of the tasks those rows stand
     * for, as far as such a task still exists.
     *
     * A binding is stored as row ids and read as **names**, the identity the
     * Sperrliste already uses. A standing task is one row per weekday, and
     * editing its weekdays adds and retires rows — the task the user ticked is
     * all of them, and a retired row is still found by id and still names it.
     * [live] takes out a task that has been ended altogether: a reward bound
     * only to tasks that no longer exist would otherwise never fill again, and
     * it counts as unbound instead.
     */
    private suspend fun boundNames(itemIds: Collection<String>, live: Set<String>): Set<String> =
        itemIds.mapNotNull { itemDao.findById(it)?.normalizedName }
            .filterTo(mutableSetOf()) { it in live }

    /** A new reward, last in the list. */
    suspend fun create(name: String, cost: Double, itemIds: Collection<String>) {
        val now = clock.now()
        val reward = Reward(
            name = name,
            cost = cost,
            position = rewardDao.lastPosition() + 1,
            createdAt = now,
            updatedAt = now,
        )
        rewardDao.upsert(reward)
        bind(reward.id, itemIds)
    }

    /**
     * Changes name, cost and binding. What has been earned stays, cut to the new
     * cost where that is lower — a reward cannot hold more than it costs.
     */
    suspend fun update(id: String, name: String, cost: Double, itemIds: Collection<String>) {
        val reward = rewardDao.findById(id) ?: return
        rewardDao.upsert(
            reward.copy(
                name = name,
                cost = cost,
                progress = reward.progress.coerceAtMost(cost),
                updatedAt = clock.now(),
            ),
        )
        bind(id, itemIds)
    }

    private suspend fun bind(rewardId: String, itemIds: Collection<String>) {
        rewardDao.clearTasks(rewardId)
        if (itemIds.isNotEmpty()) {
            rewardDao.insertTasks(
                itemIds.distinct().map { RewardTask(rewardId, it) },
            )
        }
    }

    /**
     * Writes the order the list was dragged into.
     *
     * Positions only: what each reward has earned is on its own row and does not
     * move with anything.
     */
    suspend fun reorder(ids: List<String>) {
        val now = clock.now()
        val byId = rewardDao.all().associateBy { it.id }
        rewardDao.upsertAll(
            ids.mapIndexedNotNull { index, id ->
                byId[id]?.takeIf { it.position != index + 1 }
                    ?.copy(position = index + 1, updatedAt = now)
            },
        )
    }

    /** Takes a reward that was earned in full. */
    suspend fun redeem(id: String) {
        val reward = rewardDao.findById(id) ?: return
        if (!reward.isFull || reward.isRedeemed) return
        val now = clock.now()
        rewardDao.upsert(reward.copy(redeemedAt = now, updatedAt = now))
    }

    /** Throws a reward away, and what it had earned with it. */
    suspend fun delete(id: String) {
        rewardDao.findById(id)?.let { rewardDao.delete(it) }
    }

    /** The rewards in list order, each with the task names its binding means. */
    private suspend fun targets(): List<RewardTarget> {
        val live = itemDao.findRecurringDefinitions().mapTo(mutableSetOf()) { it.normalizedName }
        val tasks = rewardDao.tasks().groupBy({ it.rewardId }, { it.itemId })
        return rewardDao.all().map { reward ->
            RewardTarget(reward, boundNames(tasks[reward.id].orEmpty(), live))
        }
    }

    /** What finishing [blocks] would pour into the list, without writing anything. */
    suspend fun outlook(blocks: List<BlockWithItem>): RewardOutlook {
        val targets = targets()
        val head = targets.firstOrNull { it.reward.isFilling }
        return RewardOutlook(
            head = head?.reward,
            headBound = head?.boundNames?.isNotEmpty() == true,
            gains = pourInto(targets, earningsOf(blocks)),
        )
    }

    /** Pours what [blocks] earned into the list, and says what went where. */
    suspend fun pour(blocks: List<BlockWithItem>): List<RewardGain> {
        val gains = pourInto(targets(), earningsOf(blocks))
        if (gains.isNotEmpty()) {
            val now = clock.now()
            rewardDao.upsertAll(gains.map { it.reward.copy(progress = it.after, updatedAt = now) })
        }
        return gains
    }
}
