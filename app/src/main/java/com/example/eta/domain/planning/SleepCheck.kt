package com.example.eta.domain.planning

import com.example.eta.domain.model.NightOverride
import com.example.eta.domain.model.PUNCTUAL_WAKE_MINUTES
import com.example.eta.domain.model.SleepLog
import com.example.eta.domain.model.SLEEP_LATE_CAP_MINUTES
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.nightEndingOn
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** How far either side of the hour of getting up the "Guten Morgen!" box stands. */
const val MORNING_WINDOW_MINUTES = 60

/**
 * What the dashboard has to ask about sleep at one moment.
 *
 * Four things, and at most one of each: the two that take over the "Gerade" box
 * while their window is open, and the two boxes that stay for the rest of the
 * day when a window closed unanswered.
 */
data class SleepPrompts(
    /**
     * The morning window is open: an hour either side of the hour of getting
     * up. The "Gerade" box says "Guten Morgen!" for all of it, answered or not —
     * [morningAnswer] is what was said.
     */
    val morning: Boolean = false,
    /** The hour of getting up that applies today. */
    val wakeDue: LocalTime? = null,
    val morningAnswer: SleepLog? = null,
    /** The window closed with nothing said: asked for until the day ends. */
    val morningOwed: Boolean = false,
    /**
     * Winding down is under way, and the day whose evening it is — the day the
     * answer is filed under. Null outside the window.
     */
    val eveningOf: LocalDate? = null,
    /** "Gehe nun schlafen" has been pressed for [eveningOf]. */
    val eveningAnswered: Boolean = false,
    /** The answer given for [eveningOf], for the box to say what it is worth. */
    val eveningAnswer: SleepLog? = null,
    /** Yesterday's bedtime was never answered: "Schlafenszeit – Vortag". */
    val bedOwedFor: LocalDate? = null,
    /** That bedtime — what the box's time picker starts at (step 59). */
    val bedDue: LocalTime? = null,
    /** Minutes past the hour that still count as on time — what the boxes say. */
    val tolerance: Int = PUNCTUAL_WAKE_MINUTES,
)

/**
 * Works out [SleepPrompts] for [now].
 *
 * Everything is read off the night as it falls on a **date**, overrides
 * included — the same night the planner shades and the alarm wakes from.
 *
 * The evening has two shapes. A bedtime before midnight puts winding down on the
 * evening of the day itself. One **after** midnight puts it — or the end of it —
 * into the small hours of the next calendar day, and it still belongs to the day
 * before: at ten past twelve on Tuesday, "Gehe nun schlafen" is how Monday ends.
 * So both nights that touch today are looked at, the one that ends today and the
 * one that ends tomorrow, each as minutes from today's midnight.
 *
 * **The tolerance** (`UserSetup.punctualTolerance`, step 48) keeps "Gehe nun
 * schlafen" on offer that many minutes past the bedtime, and holds back the
 * question about yesterday's evening as long.
 *
 * **[morningDone]** — the morning routine ticked off in full (step 48) — ends
 * the "Guten Morgen!" window early: the morning is over. A question still
 * unanswered moves at once into the box "Aufstehzeit – heute".
 */
fun sleepPrompts(
    setup: UserSetup,
    overrides: Map<LocalDate, NightOverride>,
    logs: Map<LocalDate, SleepLog>,
    now: LocalDateTime,
    morningDone: Boolean = false,
): SleepPrompts {
    val today = now.date
    val yesterday = today.minus(DatePeriod(days = 1))
    val tomorrow = today.plus(DatePeriod(days = 1))
    val minute = now.time.minuteOfDay()

    val endingToday = setup.nightEndingOn(today, overrides)
    val endingTomorrow = setup.nightEndingOn(tomorrow, overrides)

    val wake = endingToday.wake.minuteOfDay()
    val answer = logs[today]?.takeIf { it.wokeAt != null }
    val inMorning = !morningDone &&
        minute >= wake - MORNING_WINDOW_MINUTES && minute <= wake + MORNING_WINDOW_MINUTES
    val tolerance = setup.punctualTolerance.coerceAtLeast(0)
    // Step 59: going to bed late is graded up to two hours past the bedtime,
    // so "Gehe nun schlafen" stays on offer that long — pressed late, it
    // records the hour it was pressed at.
    val eveningReach = maxOf(tolerance, SLEEP_LATE_CAP_MINUTES)

    // Winding down, as minutes from today's midnight, for each night in reach.
    val evening = listOf(
        Triple(yesterday, endingToday.bedPrepOffset(), endingToday.sleepOffset() + eveningReach),
        Triple(
            today,
            endingTomorrow.bedPrepOffset() + MINUTES_PER_DAY,
            endingTomorrow.sleepOffset() + MINUTES_PER_DAY + eveningReach,
        ),
    ).firstOrNull { (_, from, until) -> minute in from until until }?.first

    // Yesterday's evening is asked about once its bedtime has passed — which a
    // bedtime before midnight has, all day, and one after it only from then on.
    val bedOwed = logs[yesterday]?.bedOnTime == null && minute >= endingToday.sleepOffset() + eveningReach

    return SleepPrompts(
        morning = inMorning,
        wakeDue = endingToday.wake,
        morningAnswer = answer,
        morningOwed = answer == null &&
            (minute > wake + MORNING_WINDOW_MINUTES || (morningDone && minute >= wake - MORNING_WINDOW_MINUTES)),
        eveningOf = evening,
        eveningAnswered = evening != null && logs[evening]?.bedOnTime != null,
        eveningAnswer = evening?.let { logs[it] }?.takeIf { it.bedOnTime != null },
        bedOwedFor = yesterday.takeIf { bedOwed },
        bedDue = endingToday.sleep,
        tolerance = tolerance,
    )
}
