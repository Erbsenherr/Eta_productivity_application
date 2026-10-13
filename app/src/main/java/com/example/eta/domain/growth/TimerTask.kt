package com.example.eta.domain.growth

import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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
