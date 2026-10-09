package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Ignore
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import java.util.UUID

/**
 * A long-term reward of the Belohn-o-mat: a name, what it costs, and how much of
 * that has been earned towards it.
 *
 * **A counter beside the account, not a withdrawal from it.** The points that
 * fill a reward are the same ones the evening credits to the balance; filling
 * and redeeming book nothing. That was the user's choice, and it is why this is
 * a table of its own rather than a kind of `PointsTransaction`.
 *
 * [progress] is stored on the row rather than summed from anywhere: the user's
 * rule is that points poured into a reward **stay** with it, whatever happens to
 * the order afterwards, and a number that belongs to the row is the only shape
 * in which that is true by construction.
 */
@Entity(tableName = "rewards")
data class Reward(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** What it costs, in points. */
    val cost: Double,
    /** Points earned towards it so far. Never more than [cost]. */
    val progress: Double = 0.0,
    /** Where it stands in the list; the lowest one still filling is "Position 1". */
    val position: Int,
    /** When the user took it. A redeemed reward is history and fills no more. */
    val redeemedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Earned in full and waiting to be taken — it no longer counts as position 1. */
    @get:Ignore
    val isFull: Boolean get() = progress >= cost - REWARD_EPSILON

    @get:Ignore
    val isRedeemed: Boolean get() = redeemedAt != null

    /** Still being filled: what the evening's points can go to. */
    @get:Ignore
    val isFilling: Boolean get() = !isRedeemed && !isFull

    @get:Ignore
    val fraction: Float
        get() = if (cost <= 0.0) 1f else (progress / cost).toFloat().coerceIn(0f, 1f)
}

/** Rounding slack, so 24.999999 of 25 counts as earned. */
const val REWARD_EPSILON = 1e-6

/**
 * One standing task a reward is bound to.
 *
 * [itemId] is a plain column with no foreign key to `items`, on purpose: a
 * standing task is one definition per weekday, rows come and go as its weekdays
 * are edited, and a retired row must go on naming the task it stood for. The
 * binding is therefore read through the **name** of the rows it points at — see
 * `boundNames` — which is the identity the Sperrliste already uses.
 */
@Entity(
    tableName = "reward_tasks",
    primaryKeys = ["rewardId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = Reward::class,
            parentColumns = ["id"],
            childColumns = ["rewardId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class RewardTask(
    val rewardId: String,
    val itemId: String,
)
