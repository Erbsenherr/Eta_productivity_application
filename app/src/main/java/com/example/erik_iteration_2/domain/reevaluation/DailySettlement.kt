package com.example.erik_iteration_2.domain.reevaluation

import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.ContractState
import com.example.erik_iteration_2.domain.model.ItemRole
import com.example.erik_iteration_2.domain.planning.MINUTES_PER_DAY
import com.example.erik_iteration_2.domain.planning.containerEndMinute
import com.example.erik_iteration_2.domain.planning.containerStartMinute
import com.example.erik_iteration_2.domain.reward.yieldOf

/** Hours a day may go unplanned before it starts costing. */
const val FREE_UNPLANNED_HOURS = 2.0

/** What every unplanned hour past [FREE_UNPLANNED_HOURS] costs. */
const val UNPLANNED_PENALTY_PER_HOUR = 1.5

/** Giving up the day's free time pays this per hour. */
const val FREE_TIME_FORGONE_PER_HOUR = 4.0

/** The most legacy contracts can pay in one day, together. */
const val LEGACY_DAILY_CAP = 2.0

/** One contract's answer in the evening check. */
data class ContractVerdict(
    val contract: Contract,
    val kept: Boolean,
)

/**
 * What the evening owes the account for one day, itemised.
 *
 * Kept as a value rather than written straight to the ledger so the reevaluation
 * can show the user the arithmetic before it happens — the concept makes a point
 * of the reward being presented, not merely applied.
 */
data class DailySettlement(
    val harvest: Double,
    val contractPayout: Double,
    /** What legacy contracts would have paid before the daily cap. */
    val legacyGross: Double,
    val legacyCredited: Double,
    val freeTimeForgone: Double,
    val unplannedHours: Double,
    /**
     * Of [unplannedHours], the part that had been planned and was then dropped.
     *
     * Reported separately because it is the half the user can still do something
     * about: empty hours were never claimed, these were promised and given back.
     */
    val droppedHours: Double,
    val unplannedPenalty: Double,
) {
    /** Legacy contracts earned more than the cap allows — the dashboard's crown. */
    val crowned: Boolean get() = legacyGross > LEGACY_DAILY_CAP

    val total: Double
        get() = harvest + contractPayout + legacyCredited + freeTimeForgone - unplannedPenalty
}

/**
 * Whether a block actually accounted for the time it occupied.
 *
 * A block that was planned and then dropped did not: the hours it was holding
 * went by like any other empty stretch, and charging for empty hours while
 * letting a broken plan pass free would make planning something and abandoning
 * it cheaper than not planning it at all. So only a **completed** block holds its
 * slot — and, if the day was left unanswered, an open one does not either.
 *
 * Free time is the one exception, and it is not a special case so much as an
 * accounting one: dropping a `FREE_TIME` block is already priced, at four points
 * an hour in [FREE_TIME_FORGONE_PER_HOUR]. Letting the unplanned charge bite it
 * as well would put two prices on one decision.
 */
fun BlockWithItem.heldItsTime(): Boolean =
    block.isCompleted || item.role == ItemRole.FREE_TIME

/**
 * How much of [date]'s available time carries a block, counting overlaps once.
 *
 * Available means everything outside the night: `Tägliche Reevaluation.md` charges
 * for "anderweitig verfügbare" hours left unplanned, and sleep was never available.
 *
 * Which blocks are worth passing in is [heldItsTime]'s question, not this one's.
 */
fun plannedMinutes(blocks: List<BlockWithItem>): Int {
    val spans = blocks
        // The container, not the task: a journey and a break are time the day
        // genuinely spends, and the charge for unplanned hours has to see it.
        .map { it.block.containerStartMinute() to it.block.containerEndMinute() }
        .filter { it.second > it.first }
        .sortedBy { it.first }
    if (spans.isEmpty()) return 0

    var total = 0
    var (from, to) = spans.first()
    for ((start, end) in spans.drop(1)) {
        if (start > to) {
            total += to - from
            from = start
            to = end
        } else {
            to = maxOf(to, end)
        }
    }
    return total + (to - from)
}

/**
 * Settles one day.
 *
 * [sleepMinutes] comes from the setup, so a day is charged only for the waking
 * hours it left empty. Free time is the mirror image: a free-time block the user
 * dropped is not a failure but a deliberate forfeit, and pays.
 *
 * [freeTimeAllowanceMinutes] caps that forfeit at the daily free time the setup
 * provides for. Without the cap, planning free time in order to skip it would be
 * the most lucrative thing in the app — 4 points an hour against the single point
 * an hour of focused work. The forfeit stays a pressure valve, not an income.
 *
 * A task that was planned and not done costs its hours as **unplanned time**; see
 * [heldItsTime]. Filling the freed slot with something else that did happen makes
 * the charge go away by itself, because planned time is a union of what stood.
 */
fun settleDay(
    blocks: List<BlockWithItem>,
    verdicts: List<ContractVerdict>,
    sleepMinutes: Int,
    freeTimeAllowanceMinutes: Int,
): DailySettlement {
    val harvest = blocks
        .filter { it.block.isCompleted }
        .sumOf { yieldOf(it.item, it.block) }

    val kept = verdicts.filter { it.kept }
    val contractPayout = kept
        .filter { it.contract.state == ContractState.ACTIVE }
        .sumOf { it.contract.dailyPayout }
    val legacyGross = kept
        .filter { it.contract.state == ContractState.LEGACY }
        .sumOf { it.contract.dailyPayout }

    val forgoneMinutes = blocks
        .filter { it.item.role == ItemRole.FREE_TIME && it.block.isDiscarded }
        .sumOf { it.block.effectiveDuration.inWholeMinutes }
        .coerceAtMost(freeTimeAllowanceMinutes.toLong())
    val freeTimeForgone = forgoneMinutes / 60.0 * FREE_TIME_FORGONE_PER_HOUR

    val availableMinutes = (MINUTES_PER_DAY - sleepMinutes).coerceAtLeast(0)
    val heldMinutes = plannedMinutes(blocks.filter { it.heldItsTime() })
    val unplannedHours = ((availableMinutes - heldMinutes).coerceAtLeast(0)) / 60.0
    val chargeable = (unplannedHours - FREE_UNPLANNED_HOURS).coerceAtLeast(0.0)

    // The difference between what the day promised and what it kept. Zero when a
    // dropped slot was covered by something that did happen.
    val droppedHours = (plannedMinutes(blocks) - heldMinutes).coerceAtLeast(0) / 60.0

    return DailySettlement(
        harvest = harvest,
        contractPayout = contractPayout,
        legacyGross = legacyGross,
        legacyCredited = legacyGross.coerceAtMost(LEGACY_DAILY_CAP),
        freeTimeForgone = freeTimeForgone,
        unplannedHours = unplannedHours,
        droppedHours = droppedHours,
        unplannedPenalty = chargeable * UNPLANNED_PENALTY_PER_HOUR,
    )
}
