package com.example.eta.domain.streak

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.HabitStreak
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.StreakKind
import com.example.eta.domain.planning.cancelledInAdvance
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus

/** What one evening of a streak pays, per day of its length (step 48). */
const val STREAK_POINTS_PER_DAY = 0.001

/** The most one streak pays in an evening. */
const val STREAK_MAX_PER_STREAK = 1.0

/** The most all streaks together pay in an evening. */
const val STREAK_MAX_TOTAL = 3.0

/** What a streak that breaks costs the account. */
const val STREAK_BREAK_PENALTY = 10.0

/** What the evening found for one streak. */
enum class StreakVerdict {
    /** Done today: one longer — or, from zero, begun. */
    EXTENDED,

    /** Not due today: neither longer nor broken. */
    NOT_DUE,

    /** Begun and still waiting for its first completion; due today and not done. */
    WAITING,

    /** Due today and not done: the flame goes out. */
    BROKEN,
}

/**
 * One streak's evening: what it was, what it is now, and what that is worth.
 *
 * [payout] is already inside the caps — see [streakOutcomes].
 */
data class StreakOutcome(
    val streak: HabitStreak,
    val verdict: StreakVerdict,
    val before: Int,
    val after: Int,
    val payout: Double = 0.0,
) {
    val penalty: Double get() = if (verdict == StreakVerdict.BROKEN) STREAK_BREAK_PENALTY else 0.0
}

/**
 * Whether a task streak's task was due on the evening's date, and whether it
 * was done.
 *
 * **Due** means an occurrence of the standing task stood in the plan — matched
 * by normalized name, the identity a reward's binding uses, since a task is one
 * row per weekday. An occurrence that does not count as due: one excused as
 * höhere Gewalt, and one called off **before its day began** — that is planning,
 * not breaking. A holiday lays no block at all. Called off on the day itself,
 * it was due and not done.
 *
 * Returns null when nothing was due, else whether any occurrence was completed.
 */
fun taskDoneOn(name: String, blocks: List<BlockWithItem>, timeZone: TimeZone): Boolean? {
    val due = blocks
        .filter { it.item.type == ItemType.RECURRING && it.item.normalizedName == name }
        .filterNot { it.block.isExcused || it.block.cancelledInAdvance(timeZone) }
    if (due.isEmpty()) return null
    return due.any { it.block.isCompleted }
}

/**
 * The evening's verdict on every running streak, with the payout capped.
 *
 * - **A task streak** is due when its task stood in the plan ([taskDoneOn]).
 * - **The sleep streak** is due every day and holds when both answers were
 *   "on time": this morning's ([wokeOnTime]) and last night's ([bedOnTime]) —
 *   the pair the settlement books. An unanswered one is not on time.
 * - **The series** is not decided here: its length comes from the settled days,
 *   which only the caller knows — see [seriesOutcome].
 *
 * A streak **waiting** for its first completion cannot break: a miss leaves it
 * waiting. One already counted for [date] is left as it is.
 *
 * Each extended streak pays [STREAK_POINTS_PER_DAY] for every day of its new
 * length, at most [STREAK_MAX_PER_STREAK]; all of them together at most
 * [STREAK_MAX_TOTAL], cut back in proportion where they would pass it.
 */
fun streakOutcomes(
    streaks: List<HabitStreak>,
    date: LocalDate,
    blocks: List<BlockWithItem>,
    wokeOnTime: Boolean,
    bedOnTime: Boolean,
    series: StreakOutcome? = null,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<StreakOutcome> {
    val outcomes = streaks
        .filter { it.isRunning && it.kind != StreakKind.SERIES }
        .filter { counted -> counted.lastCountedOn?.let { it >= date } != true }
        .map { streak ->
            val done = when (streak.kind) {
                StreakKind.TASK -> streak.taskName?.let { taskDoneOn(it, blocks, timeZone) }
                StreakKind.SLEEP -> wokeOnTime && bedOnTime
                StreakKind.SERIES -> null
            }
            val before = streak.length
            when {
                done == null -> StreakOutcome(streak, StreakVerdict.NOT_DUE, before, before)
                done -> StreakOutcome(streak, StreakVerdict.EXTENDED, before, before + 1)
                streak.isWaiting -> StreakOutcome(streak, StreakVerdict.WAITING, 0, 0)
                else -> StreakOutcome(streak, StreakVerdict.BROKEN, before, 0)
            }
        }
    return capped(listOfNotNull(series) + outcomes)
}

/**
 * The Tagesabschluss-Serie on the evening of [date], which is being settled now.
 *
 * Its length is the run of settled days ending today ([streakOf], today
 * included). **It broke** if the run no longer reaches back to the last evening
 * that counted it — a day in between went unsettled. A day made up with
 * TECHNISCHE SCHWIERIGKEITEN *before* this evening is settled and closes the
 * gap, so the series runs on: the answer the user gave. Once this evening has
 * found the break, a later catch-up changes nothing.
 *
 * A series row never counted before takes the run as it stands, unbroken: the
 * feature has just been switched on, and charging for a gap from before that
 * would be charging for a rule nobody had agreed to.
 */
fun seriesOutcome(series: HabitStreak, settled: Set<LocalDate>, date: LocalDate): StreakOutcome? {
    val last = series.lastCountedOn
    if (last != null && last >= date) return null
    val length = streakOf(settled + date, date).length
    val runStart = date.minus(DatePeriod(days = length - 1))
    val broken = last != null && last < runStart
    return StreakOutcome(
        streak = series,
        verdict = if (broken) StreakVerdict.BROKEN else StreakVerdict.EXTENDED,
        before = series.length,
        // A broken series starts again at once — this evening is settled.
        after = length,
    )
}

/** The payout of every extended streak, inside both caps. */
private fun capped(outcomes: List<StreakOutcome>): List<StreakOutcome> {
    val raw = outcomes.map { outcome ->
        if (outcome.verdict == StreakVerdict.EXTENDED) {
            (outcome.after * STREAK_POINTS_PER_DAY).coerceAtMost(STREAK_MAX_PER_STREAK)
        } else {
            0.0
        }
    }
    val total = raw.sum()
    val scale = if (total > STREAK_MAX_TOTAL) STREAK_MAX_TOTAL / total else 1.0
    return outcomes.mapIndexed { index, outcome -> outcome.copy(payout = raw[index] * scale) }
}

/**
 * The flame the tab shows during the day: the length so far, and one more once
 * today's completion is in — the evening only confirms it.
 */
fun HabitStreak.shownLength(doneToday: Boolean, today: LocalDate): Int =
    if (doneToday && lastCountedOn?.let { it >= today } != true) length + 1 else length
