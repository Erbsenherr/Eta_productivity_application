package com.example.eta.domain.growth

import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.math.roundToLong

/** What the form offers when the buffer is first ticked, and the least it may be. */
val DEFAULT_TIMER_BUFFER: Duration = 10.minutes
val MIN_TIMER_BUFFER: Duration = 1.minutes

/**
 * A Timer-Task: a standing task that is about **putting time in**, not about
 * getting something finished — "2 h lernen".
 *
 * It is worked rather than ticked off: the dashboard counts its time down, and
 * what was counted is what the evening bills. [buffer] is slack behind the time
 * on the timer, so a sitting begun a few minutes late still ends before the next
 * task begins.
 *
 * A "Zielzeit" on top of it — the time growing with every sitting worked in full
 * — is the [GrowthSetting] that used to be a kind of task of its own.
 */
data class TimerSetting(
    val buffer: Duration? = null,
)

val Item.isTimerTask: Boolean get() = timerTask

val Item.timerSetting: TimerSetting?
    get() = if (timerTask) TimerSetting(timerBuffer) else null

/** The buffer as a length; nothing for a task that has none or is no Timer-Task. */
val Item.timerBufferOrZero: Duration
    get() = if (timerTask) timerBuffer ?: Duration.ZERO else Duration.ZERO

/**
 * The time on the timer: the length without the buffer behind it.
 *
 * [Item.estimatedDuration] is what the block occupies, buffer included, so that
 * nothing about placement had to learn what a buffer is. This is the other
 * number — what the user typed, what the timer counts down from, and what
 * growing moves. For anything that is not a Timer-Task the two are the same.
 */
val Item.timerDuration: Duration?
    get() = estimatedDuration?.let { (it - timerBufferOrZero).coerceAtLeast(Duration.ZERO) }

/** Sets the flag and the buffer. The length is not touched — see [withBufferAdded]. */
fun Item.withTimer(setting: TimerSetting?): Item = copy(
    timerTask = setting != null,
    timerBuffer = setting?.buffer?.takeIf { it > Duration.ZERO },
)

/**
 * The last step of every path that saves a standing task from a form.
 *
 * The form's "Dauer" is the time on the timer, and growing works on that time
 * too; the block has to be longer by the buffer. So the paths write the length
 * as they always did — the form's answer, then `withGrowth` — and this puts the
 * buffer on top, once, at the end.
 */
fun Item.withBufferAdded(): Item {
    val length = estimatedDuration ?: return this
    return copy(estimatedDuration = length + timerBufferOrZero)
}

/**
 * Whether a finished sitting was worked **in full** — the condition for it to
 * count towards the next increment.
 *
 * Read off the two lengths a block already has. `actualDuration` is what was
 * billed: the counted time plus the buffer, or the length the user gave when
 * ticking it off by hand. Null is "as planned". A sitting ended early is
 * therefore shorter than the plan, and that is the whole test.
 */
val PlannedBlock.workedInFull: Boolean
    get() = actualDuration?.let { it >= plannedDuration - (timerExtension ?: Duration.ZERO) } ?: true

/** What "Vorzeitig beenden" takes off the time on the timer: of the time as it stands. */
const val EARLY_END_MALUS = 0.10

/** What every full sitting under a malus gives back: of the time before it. */
const val MALUS_RECOVERY = 0.02

/** A share of a duration, to the whole second — growth already counts in seconds. */
private fun Duration.share(fraction: Double): Duration =
    (inWholeSeconds * fraction).roundToLong().seconds

/** Whether a "Vorzeitig beenden" still weighs on this Timer-Task. */
val Item.hasTimerMalus: Boolean get() = timerTask && timerMalusBase != null

/**
 * "Vorzeitig beenden" (step 55): the time on the timer goes down by
 * [EARLY_END_MALUS] of what it is **now** — 60 → 54 → 48:36 — and the time it
 * stood at before the first of them is kept, as what full sittings bring it
 * back to. Never below [MIN_GROWTH_DURATION]; the buffer stays as it was.
 */
fun Item.withEarlyEndMalus(): Item {
    if (!timerTask) return this
    val current = timerDuration ?: return this
    val reduced = (current - current.share(EARLY_END_MALUS))
        .coerceAtLeast(minOf(MIN_GROWTH_DURATION, current))
    return copy(
        timerMalusBase = timerMalusBase ?: current,
        estimatedDuration = reduced + timerBufferOrZero,
    )
}

/**
 * A sitting worked in full under a malus: [MALUS_RECOVERY] of the time before
 * it comes back — 54 → 55:12 → 56:24 for a task that was at 60 — up to that
 * time and no further, where the malus ends. A time already at or past it
 * (the form changed it since) simply ends the malus.
 */
fun Item.recoveredFromMalus(): Item {
    val base = timerMalusBase ?: return this
    val current = timerDuration ?: return copy(timerMalusBase = null)
    if (current >= base) return copy(timerMalusBase = null)
    val next = minOf(current + base.share(MALUS_RECOVERY), base)
    return copy(
        estimatedDuration = next + timerBufferOrZero,
        timerMalusBase = base.takeIf { next < it },
    )
}

/**
 * One confirmed sitting of a Timer-Task, the malus taken into account — what
 * [advancedByCompletion] is for the rest.
 *
 * - **Ended early**: the malus, and the "Zielzeit" counts nothing. A
 *   Mengen-Inkrement still counts: the task was done.
 * - **Under a malus**: the time does not grow — the increment waits until the
 *   task has no malus at the moment of a sitting; a sitting worked in full
 *   brings time back instead.
 * - Otherwise as ever.
 */
fun Item.advancedBySitting(endedEarly: Boolean, workedInFull: Boolean, lenient: Boolean): Item = when {
    endedEarly -> advancedByCompletion(timeCounts = false).withEarlyEndMalus()
    hasTimerMalus -> advancedByCompletion(timeCounts = false)
        .let { if (workedInFull) it.recoveredFromMalus() else it }
    else -> advancedByCompletion(timeCounts = lenient || workedInFull)
}
