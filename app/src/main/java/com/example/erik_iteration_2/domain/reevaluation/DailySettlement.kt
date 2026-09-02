package com.example.erik_iteration_2.domain.reevaluation

import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.ContractState
import com.example.erik_iteration_2.domain.model.ItemRole
import com.example.erik_iteration_2.domain.planning.MINUTES_PER_DAY
import com.example.erik_iteration_2.domain.planning.endMinute
import com.example.erik_iteration_2.domain.planning.startMinute
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
    val unplannedPenalty: Double,
) {
    /** Legacy contracts earned more than the cap allows — the dashboard's crown. */
    val crowned: Boolean get() = legacyGross > LEGACY_DAILY_CAP

    val total: Double
        get() = harvest + contractPayout + legacyCredited + freeTimeForgone - unplannedPenalty
}

/**
 * How much of [date]'s available time carries a block, counting overlaps once.
 *
 * Available means everything outside the night: `Tägliche Reevaluation.md` charges
 * for "anderweitig verfügbare" hours left unplanned, and sleep was never available.
 */
fun plannedMinutes(blocks: List<BlockWithItem>): Int {
    val spans = blocks
        .map { it.block.startMinute() to it.block.endMinute() }
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
    val unplannedHours = ((availableMinutes - plannedMinutes(blocks)).coerceAtLeast(0)) / 60.0
    val chargeable = (unplannedHours - FREE_UNPLANNED_HOURS).coerceAtLeast(0.0)

    return DailySettlement(
        harvest = harvest,
        contractPayout = contractPayout,
        legacyGross = legacyGross,
        legacyCredited = legacyGross.coerceAtMost(LEGACY_DAILY_CAP),
        freeTimeForgone = freeTimeForgone,
        unplannedHours = unplannedHours,
        unplannedPenalty = chargeable * UNPLANNED_PENALTY_PER_HOUR,
    )
}
