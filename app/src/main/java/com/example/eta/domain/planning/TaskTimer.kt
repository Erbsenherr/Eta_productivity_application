package com.example.eta.domain.planning

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.PlannedBlock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The timer of one sitting of a Timer-Task.
 *
 * Three columns on the block and no ticking number: [PlannedBlock.timerWorked]
 * is what had been counted when the timer was last stopped, and
 * [PlannedBlock.timerRunningSince] the moment it was last set going. What has
 * been counted *now* is the one plus the time since the other, so the answer is
 * right after the app was closed for an hour, and nothing has to run in the
 * background to keep it so.
 *
 * **What counts.** Everything between "Start" and "Pause" — the pauses of a
 * pomodoro rhythm included, by the user's rule: they are part of how the time
 * is worked. A pause by hand is not counted; that is what pressing it means.
 */
enum class TimerState { IDLE, RUNNING, PAUSED }

val PlannedBlock.timerState: TimerState
    get() = when {
        timerGoal == null -> TimerState.IDLE
        timerRunningSince != null -> TimerState.RUNNING
        else -> TimerState.PAUSED
    }

/** Whether "Start" was ever pressed on this sitting. */
val PlannedBlock.timerWasUsed: Boolean get() = timerGoal != null

/**
 * What the timer has counted by [now], never more than its goal.
 *
 * Capped, because a timer left running overnight has not been worked on all
 * night: once the goal is reached there is nothing further to count towards.
 */
fun PlannedBlock.timerElapsed(now: Instant): Duration {
    val goal = timerGoal ?: return Duration.ZERO
    val running = timerRunningSince?.let { (now - it).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO
    return ((timerWorked ?: Duration.ZERO) + running).coerceIn(Duration.ZERO, goal)
}

fun PlannedBlock.timerRemaining(now: Instant): Duration =
    ((timerGoal ?: Duration.ZERO) - timerElapsed(now)).coerceAtLeast(Duration.ZERO)

/** The goal has been counted down to nothing. */
fun PlannedBlock.timerDone(now: Instant): Boolean =
    timerGoal != null && timerRemaining(now) == Duration.ZERO

fun PlannedBlock.timerStarted(goal: Duration, now: Instant): PlannedBlock =
    copy(timerGoal = goal, timerWorked = Duration.ZERO, timerRunningSince = now)

fun PlannedBlock.timerPaused(now: Instant): PlannedBlock =
    copy(timerWorked = timerElapsed(now), timerRunningSince = null)

fun PlannedBlock.timerResumed(now: Instant): PlannedBlock =
    if (timerGoal == null || timerRunningSince != null) this else copy(timerRunningSince = now)

/** A tick taken back: the sitting starts over, as if "Start" had not been pressed. */
fun PlannedBlock.timerCleared(): PlannedBlock =
    copy(timerGoal = null, timerWorked = null, timerRunningSince = null)

/**
 * What a whole sitting counts down from: the block without the buffer behind it
 * — and without whatever a late start added to it ([PlannedBlock.timerExtension]).
 *
 * The block's own length rather than the definition's, so a sitting whose length
 * was changed for one day is measured against that day.
 */
fun PlannedBlock.fullTimerGoal(buffer: Duration): Duration =
    (plannedLength - buffer).coerceAtLeast(MIN_TIMER_GOAL)

/** The block as it was planned, before a late start lengthened it. */
private val PlannedBlock.plannedLength: Duration
    get() = plannedDuration - (timerExtension ?: Duration.ZERO)

/** A timer of nothing would be over before it began. */
val MIN_TIMER_GOAL: Duration = 1.minutes

/**
 * What a sitting ended at [now] is billed: the counted time plus the buffer,
 * and never more than the block was planned at.
 *
 * The buffer pays, by the user's rule — it is time held free for the task. The
 * minutes a late start added do not: they are the lateness itself. So a sitting
 * worked in full bills exactly the length it was planned at.
 */
fun PlannedBlock.timerBilled(now: Instant, buffer: Duration): Duration {
    val counted = timerElapsed(now)
    if (counted >= fullTimerGoal(buffer)) return plannedLength
    return (counted + buffer).coerceAtMost(plannedLength)
}

/** The phase of the pomodoro rhythm the timer is in, and how long it still runs. */
data class TimerPhase(
    val kind: PomodoroPhaseKind,
    val remaining: Duration,
)

/**
 * Where the rhythm stands, counted on the **timer** rather than on the clock.
 *
 * A pomodoro on an ordinary task follows the hour the task was planned for. On
 * a Timer-Task that would be wrong twice: the sitting begins when "Start" is
 * pressed, and a pause by hand stops the rhythm with everything else. So the
 * phases are laid along the counted time — work, pause, work — and the last one
 * ends with the goal.
 *
 * Null without a rhythm, before the start, and once the goal is reached.
 */
fun PlannedBlock.timerPhase(now: Instant): TimerPhase? {
    val goal = timerGoal ?: return null
    val work = pomodoroWork?.takeIf { it > Duration.ZERO } ?: return null
    val pause = pomodoroPause?.takeIf { it > Duration.ZERO } ?: return null
    val elapsed = timerElapsed(now)
    if (elapsed >= goal) return null

    val cycle = work + pause
    val cycleStart = cycle * (elapsed / cycle).toInt()
    val into = elapsed - cycleStart
    val (kind, phaseEnd) = if (into < work) {
        PomodoroPhaseKind.WORK to cycleStart + work
    } else {
        PomodoroPhaseKind.PAUSE to cycleStart + cycle
    }
    return TimerPhase(kind, minOf(phaseEnd, goal) - elapsed)
}

/** What a running timer still has to announce. */
enum class TimerSignalKind { PAUSE, WORK, DONE }

data class TimerSignal(val kind: TimerSignalKind, val at: Instant)

/**
 * Every moment a **running** timer will have something to say: each turn of its
 * pomodoro rhythm, and the goal.
 *
 * Empty while it is paused or was never started — a stopped timer reaches
 * nothing, and the alarm is rebooked the moment it is set going again. A turn
 * that falls on the goal is left out: the goal says it.
 */
fun PlannedBlock.timerSignals(): List<TimerSignal> {
    val goal = timerGoal ?: return emptyList()
    val since = timerRunningSince ?: return emptyList()
    val worked = timerWorked ?: Duration.ZERO
    if (worked >= goal) return emptyList()

    fun at(counted: Duration): Instant = since + (counted - worked)

    val signals = mutableListOf<TimerSignal>()
    val work = pomodoroWork?.takeIf { it > Duration.ZERO }
    val pause = pomodoroPause?.takeIf { it > Duration.ZERO }
    if (work != null && pause != null) {
        var cycleStart = Duration.ZERO
        while (cycleStart < goal) {
            val pauseAt = cycleStart + work
            if (pauseAt >= goal) break
            if (pauseAt > worked) signals += TimerSignal(TimerSignalKind.PAUSE, at(pauseAt))
            val workAt = pauseAt + pause
            if (workAt >= goal) break
            if (workAt > worked) signals += TimerSignal(TimerSignalKind.WORK, at(workAt))
            cycleStart = workAt
        }
    }
    signals += TimerSignal(TimerSignalKind.DONE, at(goal))
    return signals
}

/**
 * A sitting begun so late that its time would run past the end of its block.
 *
 * The buffer exists to absorb a late start; this is what is left when it could
 * not. **The timer never runs into time nobody planned**, so there are two ways
 * on and the user picks:
 *
 * - **stop with the block** — [shortenedGoal], what is left until its end. Null
 *   when the block is already over.
 * - **lengthen the block** to [projectedEnd] — [canExtend]. Where another task
 *   stands in that stretch ([next]) it has to give way: [pushedNext] is that
 *   task beginning once the timer is through and ending when it always did, and
 *   null when it cannot — an appointment out of the calendar, or nothing worth
 *   keeping left of it.
 */
data class TimerOverflow(
    val projectedEnd: Int,
    val next: BlockWithItem?,
    val shortenedGoal: Duration?,
    val pushedNext: PlannedBlock?,
) {
    val canExtend: Boolean
        get() = projectedEnd <= MINUTES_PER_DAY && (next == null || pushedNext != null)
}

/**
 * Whether starting [entry]'s timer at [nowMinute] with [goal] would run past
 * the end of its own block — and what could be done about it.
 *
 * Null is the ordinary case: it ends inside its frame. Only the **first** block
 * standing in the stretch behind the frame is asked about; a timer that would
 * swallow it whole cannot be lengthened over it.
 */
fun timerOverflow(
    blocks: List<BlockWithItem>,
    entry: BlockWithItem,
    nowMinute: Int,
    goal: Duration,
): TimerOverflow? {
    // Rounded up: a goal of ninety seconds holds two minutes of the day.
    val projectedEnd = nowMinute + ((goal.inWholeSeconds + 59) / 60).toInt()
    val frameEnd = entry.block.endMinute()
    if (projectedEnd <= frameEnd) return null

    val next = blocks
        .filter { it.block.id != entry.block.id && it.claimsItsSlot() }
        .filter { it.block.containerEndMinute() > frameEnd }
        .filter { it.block.containerStartMinute() < projectedEnd }
        .minByOrNull { it.block.containerStartMinute() }

    return TimerOverflow(
        projectedEnd = projectedEnd,
        next = next,
        shortenedGoal = (frameEnd - nowMinute).takeIf { it >= MIN_TIMER_GOAL.inWholeMinutes }?.minutes,
        pushedNext = next?.block
            ?.takeIf { it.origin != BlockOrigin.CALENDAR_IMPORT }
            ?.trimmedAfter(projectedEnd),
    )
}

/** "1:05:09" or "24:59" — a countdown, to the second. */
fun Duration.formatCountdown(): String {
    // Rounded up, so the last second reads "00:01" until it is really over.
    val total = ((inWholeMilliseconds + 999) / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    fun two(value: Long) = value.toString().padStart(2, '0')
    return if (hours > 0) "$hours:${two(minutes)}:${two(secs)}" else "${two(minutes)}:${two(secs)}"
}
