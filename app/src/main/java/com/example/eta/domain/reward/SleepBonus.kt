package com.example.eta.domain.reward

import com.example.eta.domain.model.normalizeName

/**
 * The two half points for getting up and going to bed on time, as something a
 * reward can be bound to (step 48).
 *
 * They are not tasks — no row in `items` stands behind them — so each has a
 * fixed id of its own for `reward_tasks.itemId`, which is a plain column with no
 * foreign key for exactly this kind of reason, and a name a binding is matched
 * by like any standing task's. Unbound, they flow into the first unbound reward
 * like every other earning.
 */
enum class SleepBonus(val itemId: String, val label: String) {
    WAKE("sleep:wake", "Pünktlich aufstehen"),
    BED("sleep:bed", "Pünktlich ins Bett"),
    ;

    /** What a binding is read by — see `RewardRepository.boundNames`. */
    val taskName: String get() = normalizeName(label)

    companion object {
        fun byItemId(id: String): SleepBonus? = entries.firstOrNull { it.itemId == id }

        fun byTaskName(name: String): SleepBonus? = entries.firstOrNull { it.taskName == name }
    }
}

/**
 * What the evening's two half points earn for the Belohn-o-mat: [wakeBonus] for
 * this morning and [bedBonus] for last night — the figures the settlement books.
 */
fun sleepEarnings(wakeBonus: Double, bedBonus: Double): List<Earning> = listOfNotNull(
    Earning(SleepBonus.WAKE.taskName, standing = true, points = wakeBonus).takeIf { wakeBonus > 0.0 },
    Earning(SleepBonus.BED.taskName, standing = true, points = bedBonus).takeIf { bedBonus > 0.0 },
)
