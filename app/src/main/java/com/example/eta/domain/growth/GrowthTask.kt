package com.example.eta.domain.growth

import com.example.eta.domain.model.Item
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.overlaps
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The shortest a growth task may start at, and the smallest step it may take.
 *
 * Down to the second since step 24, at the user's request: a plank that grows by
 * ten seconds a session is exactly what this is for. The start keeps a floor of
 * one minute because the day is laid out in minutes — a block shorter than one
 * would occupy no minute at all, and placement would have nothing to place.
 * Seconds survive storage (`Converters` keeps durations in seconds); the day's
 * minute arithmetic simply rounds them down.
 */
val MIN_GROWTH_DURATION: Duration = 1.minutes
val MIN_GROWTH_INCREMENT: Duration = 1.seconds

/** What the dialog offers when the Growth-Task box is first ticked. */
val DEFAULT_GROWTH_START: Duration = 15.minutes
val DEFAULT_GROWTH_TARGET: Duration = 60.minutes
val DEFAULT_GROWTH_INCREMENT: Duration = 5.minutes

/**
 * The three lengths a growth task is set up with, plus how it is placed.
 *
 * Lengths, not clock times. The task keeps the start time every standing task
 * has; what grows is how long it runs — which is why nothing outside this
 * package has to know about growth at all: [Item.estimatedDuration] is the
 * current length, and expansion, placement, the yield and the settlement all go
 * on asking exactly what they asked before.
 */
data class GrowthSetting(
    val start: Duration,
    val target: Duration,
    val increment: Duration,
    /** [Item.startTime] is the earliest hour rather than the hour. */
    val dynamic: Boolean,
    /** The update condition: the increment is added every this many completions. */
    val every: Int = DEFAULT_EVERY,
) {
    /** A setting that cannot grow is not one; the form is what keeps this true. */
    val isSane: Boolean
        get() = start >= MIN_GROWTH_DURATION &&
            increment >= MIN_GROWTH_INCREMENT &&
            target >= start

    companion object {
        val Default = GrowthSetting(
            start = DEFAULT_GROWTH_START,
            target = DEFAULT_GROWTH_TARGET,
            increment = DEFAULT_GROWTH_INCREMENT,
            dynamic = false,
        )
    }
}

/**
 * Whether this definition grows.
 *
 * A target and an increment are what growing takes; [Item.growthStart] is only
 * the record of where it began and is never the thing that decides.
 */
val Item.isGrowthTask: Boolean
    get() = growthTarget != null && growthIncrement != null

val Item.growthSetting: GrowthSetting?
    get() {
        val target = growthTarget ?: return null
        val increment = growthIncrement ?: return null
        return GrowthSetting(
            start = growthStart ?: estimatedDuration ?: target,
            target = target,
            increment = increment,
            dynamic = growthDynamic,
            every = growthEvery,
        )
    }

/** Where the length stands now — the block's own length, as everything else sees it. */
val Item.growthCurrent: Duration?
    get() = if (isGrowthTask) estimatedDuration else null

/** Nothing left to grow: the target has been reached. */
val Item.isFullyGrown: Boolean
    get() = isGrowthTask && (estimatedDuration ?: Duration.ZERO) >= (growthTarget ?: Duration.ZERO)

/**
 * The length after one more confirmed evening, capped at the target.
 *
 * Null when this does not grow, and the current length when it is already there —
 * so the caller can compare and write nothing when nothing moved.
 */
fun Item.grownDuration(): Duration? {
    val target = growthTarget ?: return null
    val increment = growthIncrement ?: return null
    val current = estimatedDuration ?: return null
    return minOf(current + increment, target)
}

/**
 * Sets the three lengths, or takes them off again.
 *
 * Switching growth **on** puts the current length at the start: a task that is
 * to grow from a quarter of an hour has to begin at a quarter of an hour, or the
 * first thing the feature would do is make the task shorter than it was without
 * warning. Switching it **off** leaves the length exactly where growing brought
 * it, because that is the length the user has been doing.
 */
fun Item.withGrowth(setting: GrowthSetting?): Item = when (setting) {
    null -> copy(
        growthStart = null,
        growthTarget = null,
        growthIncrement = null,
        growthDynamic = false,
        growthEvery = DEFAULT_EVERY,
        growthProgress = 0,
    )

    else -> copy(
        growthStart = setting.start,
        growthTarget = setting.target,
        growthIncrement = setting.increment,
        growthDynamic = setting.dynamic,
        growthEvery = setting.every.coerceIn(DEFAULT_EVERY, MAX_EVERY),
        // The completions counted towards the next step start again whenever the
        // length itself is put back to the start.
        growthProgress = if (!isGrowthTask || growthStart != setting.start) 0 else growthProgress,
        // Growing has not begun yet, or the start was moved: either way the
        // current length is the start. An existing growth task that is merely
        // edited keeps where it has got to, unless the start overtook it.
        estimatedDuration = when {
            !isGrowthTask -> setting.start
            growthStart != setting.start -> setting.start
            else -> (estimatedDuration ?: setting.start)
                .coerceIn(setting.start, maxOf(setting.start, setting.target))
        },
    )
}

/**
 * How the growth tasks of one day are ordered when two of them want the same
 * slot: the order of the Growth-Tasks tab, and a stable fallback under it.
 *
 * Lower first. A definition that has never been dragged in that tab has no
 * number and sorts last — being new is exactly the weaker claim.
 */
val growthOrdering: Comparator<Item> = compareBy(
    { it.growthOrder ?: Int.MAX_VALUE },
    { it.name },
    { it.id },
)

/**
 * What makes two rows the same growth task.
 *
 * The model keeps one definition per weekday, so a growth task on Tuesday and
 * Thursday is two rows. Growing only the row whose occurrence was completed
 * would be wrong twice over: the user said "with every completion", meaning the
 * task, and the two rows would then hold different lengths and be listed as two
 * tasks — the standing schedule groups by attributes, and the length is one of
 * them.
 *
 * Name plus the three growth answers, because those are what the user typed once
 * for the whole task. `growthOrder` would be tidier but is null for a growth task
 * made outside the Growth-Tasks tab.
 */
data class GrowthFamily(
    val normalizedName: String,
    val target: Duration?,
    val increment: Duration?,
    val every: Int = DEFAULT_EVERY,
)

val Item.growthFamily: GrowthFamily
    get() = GrowthFamily(normalizedName, growthTarget, growthIncrement, growthEvery)

/** Half-open minute span of a day; what placement works in. */
data class MinuteSpan(val from: Int, val to: Int) {
    fun overlaps(other: MinuteSpan): Boolean = overlaps(from, to, other.from, other.to)
}

/**
 * The first minute at or after [earliest] where a task of [length] fits, with
 * its journey in front of it and its return and break behind.
 *
 * Returns the **task's** start; the container reaches back by [lead]. Deliberately
 * **not** snapped to the grid: the point of dynamic placement is a gapless chain,
 * and rounding each link up to the next quarter of an hour would put a gap
 * between every pair of them. Null when the rest of the day has no room.
 */
fun firstFreeGrowthStart(
    obstacles: List<MinuteSpan>,
    earliest: Int,
    length: Int,
    lead: Int = 0,
    tail: Int = 0,
): Int? {
    if (length <= 0) return null
    val sorted = obstacles.sortedBy { it.from }
    var candidate = maxOf(earliest, lead).coerceAtLeast(0)
    while (candidate + length + tail <= MINUTES_PER_DAY) {
        val span = MinuteSpan(candidate - lead, candidate + length + tail)
        val blocking = sorted.firstOrNull { it.overlaps(span) } ?: return candidate
        candidate = blocking.to + lead
    }
    return null
}
