package com.example.eta.domain.stats

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.growth.workedInFull
import com.example.eta.domain.model.SleepLog
import kotlinx.datetime.LocalDate

/** How far a week's share has to stand from the usual one to count as better or worse. */
const val REVIEW_MARGIN_POINTS = 5

/** Where a week stands against the weeks before it. */
enum class Trend { BETTER, USUAL, WORSE, UNKNOWN }

/** One figure of the Wochenrückblick: the week, and everything before it. */
data class ReviewFigure(
    val label: String,
    val week: Share,
    val before: Share,
) {
    /**
     * Better, worse, or as usual — by [REVIEW_MARGIN_POINTS] percentage
     * points. Unknown where either side counted nothing.
     */
    val trend: Trend
        get() {
            val now = week.percent ?: return Trend.UNKNOWN
            val usual = before.percent ?: return Trend.UNKNOWN
            return when {
                now - usual >= REVIEW_MARGIN_POINTS -> Trend.BETTER
                usual - now >= REVIEW_MARGIN_POINTS -> Trend.WORSE
                else -> Trend.USUAL
            }
        }
}

/** The Wochenrückblick (step 58): last week's figures beside the usual ones. */
data class WeekReview(
    val from: LocalDate,
    val to: LocalDate,
    /** Tasks taken on last week that were finished — the number itself. */
    val tasksDone: Int,
    val figures: List<ReviewFigure>,
    /** Whether anything at all was planned before last week to compare with. */
    val hasHistory: Boolean,
) {
    /** Nothing stood on last week: the page says so instead of showing dashes. */
    val isEmpty: Boolean get() = figures.all { it.week.of == 0 }
}

/**
 * Of the tasks taken on, how many happened as laid down — finished, not
 * changed beforehand and, for a Timer-Task, worked in full. "Wie akkurat
 * alles lief"; the strict reading `taskStats` uses, over every task.
 */
fun accuracy(blocks: List<BlockWithItem>): Share {
    val counted = blocks.filter { it.isTakenOnTask() && !it.block.isExcused }
    return Share(
        counted.count { it.block.isCompleted && it.block.planChanged() != true && it.block.workedInFull },
        counted.size,
    )
}

/**
 * Last week ([from]..[to], days that are over) against **every day before
 * it** — the user's own usual, not an ideal. [blocks] and [logs] are the whole
 * history up to [to]; [today] cuts off what is not over yet.
 */
fun weekReview(
    blocks: List<BlockWithItem>,
    logs: List<SleepLog>,
    from: LocalDate,
    to: LocalDate,
    today: LocalDate,
    sleepMinutesOn: (LocalDate) -> Int,
): WeekReview {
    val over = blocks.filter { it.block.isOver(today) }
    val weekBlocks = over.filter { it.block.date in from..to }
    val earlier = over.filter { it.block.date < from }
    val weekLogs = logs.filter { it.date in from..to && it.date <= today }
    val earlierLogs = logs.filter { it.date < from }

    val week = overview(weekBlocks, weekLogs, sleepMinutesOn)
    val before = overview(earlier, earlierLogs, sleepMinutesOn)

    return WeekReview(
        from = from,
        to = to,
        tasksDone = weekBlocks.count { it.isTakenOnTask() && it.block.isCompleted },
        figures = listOf(
            ReviewFigure("Aufgaben abgeschlossen", week.completed, before.completed),
            ReviewFigure("Planmäßig erledigt", accuracy(weekBlocks), accuracy(earlier)),
            ReviewFigure("Pünktlich aufgestanden", week.wake, before.wake),
            ReviewFigure("Pünktlich schlafen gegangen", week.bed, before.bed),
            ReviewFigure("Zeit verplant", week.planned, before.planned),
        ),
        hasHistory = earlier.isNotEmpty(),
    )
}
