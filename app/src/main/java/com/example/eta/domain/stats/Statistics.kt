package com.example.eta.domain.stats

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.growth.hasQuantity
import com.example.eta.domain.growth.timerBufferOrZero
import com.example.eta.domain.growth.workedInFull
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.SleepLog
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.occupiesTime
import com.example.eta.domain.reevaluation.plannedMinutes
import com.example.eta.domain.setup.UserSetup
import kotlin.time.Duration
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

/**
 * The stretch of the past a statistic looks at.
 *
 * Counted back from yesterday: today is still being lived, and a day half over
 * would read as a day half failed.
 */
enum class StatRange(val label: String, val days: Int?) {
    WEEK("7 Tage", 7),
    MONTH("Monat", 30),
    YEAR("Jahr", 365),
    ALL("Seit Beginn", null),
    ;

    /** The first day counted, or null for "everything there is". */
    fun from(today: LocalDate): LocalDate? = days?.let { today.minus(DatePeriod(days = it)) }
}

/**
 * The contents of the Statistiken tab, each of which the settings can hide.
 *
 * `UserSetup.statsHidden` holds the names of the hidden ones — hidden rather
 * than shown, so a section added in a later round is on for everyone.
 */
enum class StatSection(val label: String) {
    WAKE("Pünktlich aufgestanden"),
    BED("Pünktlich schlafen gegangen"),
    PLANNED("Anteil verplanter Zeit"),
    COMPLETED("Abgeschlossene Aufgaben"),
    CATEGORIES("Zeit je Kategorie"),
    RECURRING("Wiederkehrende Tasks"),
    GOALS("Timer- und Inkrement-Tasks"),
}

private fun UserSetup.hiddenStats(): Set<String> =
    statsHidden.split(',').filter { it.isNotBlank() }.toSet()

fun UserSetup.showsStat(section: StatSection): Boolean = section.name !in hiddenStats()

fun UserSetup.withStat(section: StatSection, shown: Boolean): UserSetup {
    val hidden = if (shown) hiddenStats() - section.name else hiddenStats() + section.name
    return copy(statsHidden = hidden.sorted().joinToString(","))
}

/** So many out of so many. [percent] is null where there was nothing to count. */
data class Share(val hits: Int, val of: Int) {
    val percent: Int? get() = if (of == 0) null else (hits * 100.0 / of + 0.5).toInt()
}

/** The four figures of the info box, and the hours behind "Zeit je Kategorie". */
data class Overview(
    /** Mornings answered, and how many of them were on time. */
    val wake: Share,
    /** Evenings answered, and how many of them were on time. */
    val bed: Share,
    /** Waking minutes of the days counted, and how many of them carried a plan. */
    val planned: Share,
    /** Tasks taken on, and how many were finished. */
    val completed: Share,
    val categoryMinutes: Map<Category, Int>,
)

/**
 * Whether a block is a task the user "took on" — what the completion rate is
 * about, by the user's own choice: ToDos, standing tasks and appointments, and
 * not the frame of the day. The frame carries no category, which is the handle;
 * an imported appointment pays by the hour and counts whatever its category.
 */
fun BlockWithItem.isTakenOnTask(): Boolean =
    item.category != null || block.origin == BlockOrigin.CALENDAR_IMPORT

/**
 * The overview over [blocks] and [logs] — both already cut to the range, and to
 * days that are over.
 *
 * - **Verplante Zeit** is what stood on a day — a cancellation gave its hours
 *   back and is not in it — against that day's waking minutes. Only days that
 *   held any block are counted: before the app was in use there is no plan to
 *   be a share of anything.
 * - **Abgeschlossen** leaves out what was called off for höhere Gewalt. That
 *   was nobody's doing, and it should move the rate in neither direction.
 */
fun overview(
    blocks: List<BlockWithItem>,
    logs: List<SleepLog>,
    sleepMinutesOn: (LocalDate) -> Int,
): Overview {
    val woke = logs.mapNotNull { it.wokeOnTime }
    val bed = logs.mapNotNull { it.bedOnTime }

    var available = 0
    var planned = 0
    blocks.groupBy { it.block.date }.forEach { (date, day) ->
        val awake = (MINUTES_PER_DAY - sleepMinutesOn(date)).coerceAtLeast(0)
        available += awake
        planned += plannedMinutes(day.filter { it.occupiesTime() }).coerceAtMost(awake)
    }

    val tasks = blocks.filter { it.isTakenOnTask() && !it.block.isExcused }
    val done = blocks.filter { it.block.isCompleted }

    return Overview(
        wake = Share(woke.count { it }, woke.size),
        bed = Share(bed.count { it }, bed.size),
        planned = Share(planned, available),
        completed = Share(tasks.count { it.block.isCompleted }, tasks.size),
        categoryMinutes = done
            .filter { it.item.category != null }
            .groupBy { it.item.category!! }
            .mapValues { (_, list) -> list.sumOf { it.block.effectiveDuration.inWholeMinutes.toInt() } },
    )
}

/**
 * Whether an occurrence was changed after the schedule laid it down — moved,
 * lengthened, shortened, by hand or because something else was put on top of it.
 *
 * Null where that cannot be known: a block from before the originals were
 * stamped (step 43), or one the schedule did not lay down at all.
 *
 * [ignoreStart] is for a task placed dynamically. Its hour is worked out for it
 * day by day, so a start that differs from the definition's is the app's doing.
 */
fun PlannedBlock.planChanged(ignoreStart: Boolean = false): Boolean? {
    val length = originalDuration ?: return null
    if (plannedDuration != length) return true
    return !ignoreStart && originalStart != null && start != originalStart
}

/** What one standing task did over a stretch of the past. */
data class TaskStats(
    /** The time put in: what the finished occurrences were billed at. */
    val time: Duration,
    /** Occurrences that came and went, and how many of them did not happen. */
    val skipped: Share,
    /** Of the occurrences whose original plan is known, how many were changed. */
    val changed: Share,
    /** How many happened exactly as laid down. */
    val asPlanned: Share,
)

/**
 * [TaskStats] over the occurrences of one task — already cut to the range and
 * to what is over (see [isOver]).
 *
 * An occurrence called off for höhere Gewalt is left out of all three rates.
 * "As planned" is the strict reading: finished, not changed beforehand, and —
 * for a Timer-Task — worked in full.
 */
fun taskStats(blocks: List<PlannedBlock>, dynamic: Boolean = false): TaskStats {
    val counted = blocks.filterNot { it.isExcused }
    val known = counted.filter { it.planChanged(dynamic) != null }
    return TaskStats(
        time = counted.filter { it.isCompleted }
            .fold(Duration.ZERO) { sum, block -> sum + block.effectiveDuration },
        skipped = Share(counted.count { !it.isCompleted }, counted.size),
        changed = Share(known.count { it.planChanged(dynamic) == true }, known.size),
        asPlanned = Share(
            counted.count { it.isCompleted && it.planChanged(dynamic) != true && it.workedInFull },
            counted.size,
        ),
    )
}

/** A day that is past, or an occurrence of today that has already been answered. */
fun PlannedBlock.isOver(today: LocalDate): Boolean = date < today || (date == today && !isOpen)

/**
 * The rows that are one task to the user: every live definition of a name.
 *
 * By name rather than by the attributes the standing schedule groups on — there
 * a Tuesday at six and a Thursday at seven are two entries, because they are
 * edited apart. To a statistic they are the same thing done twice a week.
 */
fun tasksByName(definitions: List<Item>): List<List<Item>> =
    definitions
        .filter { it.completedAt == null && it.isConcretized }
        .filter { it.stage != Stage.COLLECTION && it.stage != Stage.LOCKED }
        .groupBy { it.normalizedName }
        .values
        .sortedBy { it.first().name.lowercase() }

/** One point of a task's workload over time. */
data class PensumPoint(val date: LocalDate, val value: Double)

/**
 * How long a Timer-Task's timer was set, occurrence by occurrence, in minutes.
 *
 * Read off the blocks, which keep the length they were laid down with — so this
 * reaches back before step 43. A called-off occurrence still shows what the
 * workload *was*; the buffer and a late start's extension are not workload.
 */
fun timerPensum(blocks: List<PlannedBlock>, item: Item): List<PensumPoint> =
    blocks.sortedBy { it.date }.map { block ->
        val length = block.plannedDuration - (block.timerExtension ?: Duration.ZERO) - item.timerBufferOrZero
        PensumPoint(block.date, length.coerceAtLeast(Duration.ZERO).inWholeSeconds / 60.0)
    }

/**
 * The count a Mengen-Inkrement task was done with, day by day.
 *
 * Only where it was stamped — at an evening's settlement, from step 43 on — and
 * with today's standing count as the last point, so a task with no history yet
 * still shows where it is.
 */
fun quantityPensum(blocks: List<PlannedBlock>, item: Item, today: LocalDate): List<PensumPoint> {
    val past = blocks
        .filter { it.quantityDone != null && it.date < today }
        .sortedBy { it.date }
        .map { PensumPoint(it.date, it.quantityDone!!.toDouble()) }
    val now = item.quantity?.takeIf { item.hasQuantity }?.let { PensumPoint(today, it.toDouble()) }
    return past + listOfNotNull(now)
}

/**
 * Whether a plot over [from]..[to] should name months along its axis rather
 * than days — "immer das, was besser passt" for "seit Erstellung". Two months
 * of days are still readable; beyond that the months are.
 */
fun monthsFitBetter(from: LocalDate, to: LocalDate): Boolean = from.daysUntil(to) > 62
