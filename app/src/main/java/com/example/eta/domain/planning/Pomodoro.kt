package com.example.eta.domain.planning

import com.example.eta.domain.model.PlannedBlock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.LocalTime

/** What the dialog suggests: half an hour on, five minutes off. */
val DEFAULT_POMODORO_WORK: Duration = 30.minutes
val DEFAULT_POMODORO_PAUSE: Duration = 5.minutes

/**
 * The shortest either phase may be. Both are freely chosen, so the floor is only
 * what the day can still express: it is laid out in whole minutes, and a phase
 * shorter than one would announce nothing at all.
 */
val MIN_POMODORO_PHASE: Duration = 1.minutes

enum class PomodoroPhaseKind { WORK, PAUSE }

/** One boundary inside a task: a pause beginning, or work beginning again. */
data class PomodoroBoundary(
    val kind: PomodoroPhaseKind,
    val minuteOfDay: Int,
)

/** The phase a minute falls in, and the minute it ends. */
data class PomodoroPhase(
    val kind: PomodoroPhaseKind,
    val untilMinute: Int,
)

val PlannedBlock.hasPomodoro: Boolean
    get() = pomodoroWork != null && pomodoroPause != null

/**
 * Where the rhythm is counted from.
 *
 * Null on the block means the task's own start, which is what a pomodoro set up
 * ahead of time asks for — and it is the right thing to store rather than a copy
 * of the start, because a block moved afterwards should take its rhythm with it.
 * One set up in the middle of a task counts from the minute it was set up.
 */
fun PlannedBlock.pomodoroAnchorMinute(): Int =
    (pomodoroAnchor ?: start).minuteOfDay().coerceIn(startMinute(), endMinute())

/**
 * Every boundary the rhythm has left to announce, inside the task.
 *
 * Only inside the **task**: the journeys and the break around it are not work,
 * and a rhythm that ran on into the way home would be counting the wrong thing.
 * A boundary on the task's last minute is left out — that minute is the end of
 * the task, which announces itself.
 *
 * The rhythm's own first work phase is never announced. Set up ahead, the task's
 * start sound already says it; set up mid-task, the user has just pressed the
 * button. So work is announced from the end of the first pause on, and every
 * pause is — the first one included.
 */
fun PlannedBlock.pomodoroBoundaries(): List<PomodoroBoundary> {
    val work = pomodoroWork?.inWholeMinutes?.toInt() ?: return emptyList()
    val pause = pomodoroPause?.inWholeMinutes?.toInt() ?: return emptyList()
    if (work <= 0 || pause <= 0) return emptyList()

    val end = endMinute()
    val boundaries = mutableListOf<PomodoroBoundary>()
    var cycleStart = pomodoroAnchorMinute()
    while (true) {
        val pauseStart = cycleStart + work
        if (pauseStart >= end) break
        boundaries += PomodoroBoundary(PomodoroPhaseKind.PAUSE, pauseStart)
        val workStart = pauseStart + pause
        if (workStart >= end) break
        boundaries += PomodoroBoundary(PomodoroPhaseKind.WORK, workStart)
        cycleStart = workStart
    }
    return boundaries
}

/**
 * Which phase of the rhythm [minuteOfDay] falls in, for the dashboard to say.
 *
 * Null outside the task, or before the anchor — the rhythm has not begun then.
 */
fun PlannedBlock.pomodoroPhaseAt(minuteOfDay: Int): PomodoroPhase? {
    if (!hasPomodoro) return null
    val anchor = pomodoroAnchorMinute()
    val end = endMinute()
    if (minuteOfDay < anchor || minuteOfDay >= end) return null

    var kind = PomodoroPhaseKind.WORK
    for (boundary in pomodoroBoundaries()) {
        if (boundary.minuteOfDay > minuteOfDay) return PomodoroPhase(kind, boundary.minuteOfDay)
        kind = boundary.kind
    }
    return PomodoroPhase(kind, end)
}

/**
 * The block with a rhythm set up, from [now] if the task is already running.
 *
 * Anything before the task has begun counts from its start and stores no anchor,
 * so moving the block later moves the rhythm with it. Seconds are dropped: the
 * announcements ring on whole minutes like everything else in the day.
 */
fun PlannedBlock.withPomodoro(
    work: Duration,
    pause: Duration,
    now: LocalTime?,
): PlannedBlock {
    val anchor = now
        ?.let { LocalTime(it.hour, it.minute) }
        ?.takeIf { it.minuteOfDay() > startMinute() }
    return copy(pomodoroWork = work, pomodoroPause = pause, pomodoroAnchor = anchor)
}

fun PlannedBlock.withoutPomodoro(): PlannedBlock =
    copy(pomodoroWork = null, pomodoroPause = null, pomodoroAnchor = null)
