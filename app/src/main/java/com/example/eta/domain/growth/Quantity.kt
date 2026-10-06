package com.example.eta.domain.growth

import com.example.eta.domain.model.Item

/** What the form offers when the Mengen-Inkrement box is first ticked. */
const val DEFAULT_QUANTITY_START = 1
const val DEFAULT_QUANTITY_INCREMENT = 1

/** The smallest count a task may start at, and the smallest step it may take. */
const val MIN_QUANTITY = 1

/** The update condition: an increment every [DEFAULT_EVERY] confirmed completions. */
const val DEFAULT_EVERY = 1
const val MAX_EVERY = 99

/**
 * A Mengen-Inkrement: a count the task is done with, growing like a growth task
 * grows — "Klimmzüge, beginnend mit 1, +1 bei jeder Absolvierung".
 *
 * The count is **not** a duration and changes nothing about the day: the block
 * keeps its length, the yield keeps its rate. It is something the task says
 * about itself, which is why it lives on the definition and is read by every
 * occurrence rather than being stamped onto blocks — tomorrow's block shows the
 * count tomorrow's completion will be asked for, however far ahead it was laid
 * down.
 *
 * [target] is optional: a count may simply go on growing.
 */
data class QuantitySetting(
    val start: Int,
    val increment: Int,
    /** Increment every this many confirmed completions — the update condition. */
    val every: Int = DEFAULT_EVERY,
    val target: Int? = null,
) {
    companion object {
        val Default = QuantitySetting(
            start = DEFAULT_QUANTITY_START,
            increment = DEFAULT_QUANTITY_INCREMENT,
        )
    }
}

val Item.hasQuantity: Boolean
    get() = quantity != null && quantityIncrement != null

val Item.quantitySetting: QuantitySetting?
    get() {
        val increment = quantityIncrement ?: return null
        val current = quantity ?: return null
        return QuantitySetting(
            start = quantityStart ?: current,
            increment = increment,
            every = quantityEvery,
            target = quantityTarget,
        )
    }

/** Nothing left to count up to: there is a target and it has been reached. */
val Item.isQuantityReached: Boolean
    get() {
        val target = quantityTarget ?: return false
        return hasQuantity && (quantity ?: 0) >= target
    }

/**
 * Sets the count up, or takes it off again — the shape of [withGrowth].
 *
 * Switching it on, or moving the start, puts the count at the start; an edit
 * that leaves the start alone keeps where the task has got to, capped by a
 * target that may have been lowered. Either way the completions counted towards
 * the next step start again from nothing when the count itself is reset.
 */
fun Item.withQuantity(setting: QuantitySetting?): Item = when (setting) {
    null -> copy(
        quantity = null,
        quantityStart = null,
        quantityIncrement = null,
        quantityTarget = null,
        quantityEvery = DEFAULT_EVERY,
        quantityProgress = 0,
    )

    else -> {
        val restart = !hasQuantity || quantityStart != setting.start
        val current = if (restart) {
            setting.start
        } else {
            val now = quantity ?: setting.start
            setting.target?.let { now.coerceAtMost(maxOf(it, setting.start)) } ?: now
        }
        copy(
            quantity = current,
            quantityStart = setting.start,
            quantityIncrement = setting.increment,
            quantityTarget = setting.target?.let { maxOf(it, setting.start) },
            quantityEvery = setting.every.coerceIn(DEFAULT_EVERY, MAX_EVERY),
            quantityProgress = if (restart) 0 else quantityProgress,
        )
    }
}

/**
 * One confirmed completion, counted against an update condition of [every].
 *
 * Returns the progress to store and whether the increment applies now. With
 * `every = 2` the first completion only counts, the second adds the step and
 * starts the count again.
 */
fun countCompletion(progress: Int, every: Int): Pair<Int, Boolean> {
    val next = progress + 1
    return if (next >= every.coerceAtLeast(1)) 0 to true else next to false
}

/**
 * The definition after one more confirmed completion: growth and count both
 * advanced by their own update condition.
 *
 * A growth task that has reached its target, or a count that has reached its
 * own, stops counting as well — there is nothing left for the count to lead to.
 */
fun Item.advancedByCompletion(): Item {
    var result = this
    if (isGrowthTask && !isFullyGrown) {
        val (progress, fires) = countCompletion(growthProgress, growthEvery)
        result = result.copy(
            growthProgress = progress,
            estimatedDuration = if (fires) grownDuration() else estimatedDuration,
        )
    }
    if (hasQuantity && !isQuantityReached) {
        val (progress, fires) = countCompletion(quantityProgress, quantityEvery)
        val current = quantity ?: 0
        val next = if (fires) {
            val raised = current + (quantityIncrement ?: 0)
            quantityTarget?.let { minOf(raised, it) } ?: raised
        } else {
            current
        }
        result = result.copy(quantityProgress = progress, quantity = next)
    }
    return result
}

/**
 * What makes two rows the same counted task — [GrowthFamily]'s counterpart.
 *
 * The count is written to every weekday's row, so Klimmzüge on Monday and
 * Wednesday share one number and one progress towards the next step.
 */
data class QuantityFamily(
    val normalizedName: String,
    val start: Int?,
    val increment: Int?,
    val every: Int,
    val target: Int?,
)

val Item.quantityFamily: QuantityFamily
    get() = QuantityFamily(normalizedName, quantityStart, quantityIncrement, quantityEvery, quantityTarget)

/** "Anzahl 12", or "Anzahl 12 / 20" with a target. */
fun Item.quantityLabel(): String? {
    val current = quantity ?: return null
    if (!hasQuantity) return null
    return quantityTarget?.let { "Anzahl $current / $it" } ?: "Anzahl $current"
}
