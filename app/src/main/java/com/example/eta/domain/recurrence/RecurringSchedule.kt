package com.example.eta.domain.recurrence

import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.WeekParity
import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.containerEndMinute
import com.example.eta.domain.planning.containerStartMinute
import com.example.eta.domain.planning.minuteOfDay
import com.example.eta.domain.planning.overlaps
import com.example.eta.domain.setup.WEEK
import kotlin.time.Duration
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The time a standing task claims on each of its days, as a planner would see it.
 *
 * The same container every block is asked about — journey there, task, journey
 * back, break — and clamped to the day the same way, so a warning here and a
 * collision in the planner can never disagree about what counts as overlapping.
 */
data class RecurringSlot(
    val rule: RecurrenceRule,
    val startTime: LocalTime,
    val duration: Duration,
    val travelBefore: Duration? = null,
    val returnAfter: Duration? = null,
    val breakAfter: Duration? = null,
) {
    fun containerStartMinute(): Int = containerStartMinute(startTime.minuteOfDay(), travelBefore)

    fun containerEndMinute(): Int {
        val end = (startTime.minuteOfDay() + duration.inWholeMinutes.toInt()).coerceAtMost(MINUTES_PER_DAY)
        return containerEndMinute(end, returnAfter, breakAfter)
    }

    companion object {
        /** The slot a definition lays down, or null when it lays down nothing. */
        fun of(item: Item): RecurringSlot? {
            val rule = item.recurrenceRule ?: return null
            val start = item.startTime ?: return null
            val duration = item.estimatedDuration ?: return null
            return RecurringSlot(rule, start, duration, item.travelBefore, item.returnAfter, item.breakAfter)
        }
    }
}

/** Another standing task that wants the same time, and on which weekdays. */
data class RecurringOverlap(
    val other: Item,
    val weekdays: Set<DayOfWeek>,
)

/**
 * The weekdays on which two rules can land on the same date.
 *
 * Sharing a weekday is necessary but not enough: a fortnightly task in even
 * weeks never meets one in odd weeks, and the second Tuesday of a month never
 * meets the fourth. Every other pairing does meet sooner or later — a monthly
 * Tuesday falls into one parity or the other — and a warning that is sometimes
 * true is still worth giving.
 */
fun RecurrenceRule.sharedWeekdays(other: RecurrenceRule): Set<DayOfWeek> {
    val common = weekdaysOf() intersect other.weekdaysOf()
    if (common.isEmpty()) return emptySet()
    val disjoint = when {
        this is RecurrenceRule.Biweekly && other is RecurrenceRule.Biweekly -> parity != other.parity
        this is RecurrenceRule.Monthly && other is RecurrenceRule.Monthly -> weekOfMonth != other.weekOfMonth
        else -> false
    }
    return if (disjoint) emptySet() else common
}

/**
 * Which standing tasks a new or edited one would collide with.
 *
 * Asked while the form is still open, so the answer is a warning rather than a
 * refusal: two things at once can be deliberate — a walk during a phone call —
 * and the planner's own rules still apply to every occurrence either way.
 *
 * [candidates] is what the form describes, one slot per rule it will become.
 * [ignoreIds] are the rows being edited, which would otherwise collide with
 * themselves.
 */
fun recurringOverlaps(
    candidates: List<RecurringSlot>,
    definitions: List<Item>,
    ignoreIds: Set<String> = emptySet(),
): List<RecurringOverlap> {
    val byItem = linkedMapOf<String, Pair<Item, MutableSet<DayOfWeek>>>()
    definitions
        .filter { it.id !in ignoreIds && it.completedAt == null && it.stage != Stage.COLLECTION }
        .forEach { other ->
            val slot = RecurringSlot.of(other) ?: return@forEach
            candidates.forEach { candidate ->
                val days = candidate.rule.sharedWeekdays(slot.rule)
                if (days.isEmpty()) return@forEach
                val clash = overlaps(
                    candidate.containerStartMinute(),
                    candidate.containerEndMinute(),
                    slot.containerStartMinute(),
                    slot.containerEndMinute(),
                )
                if (clash) byItem.getOrPut(other.id) { other to mutableSetOf() }.second += days
            }
        }
    return byItem.values
        .map { (item, days) -> RecurringOverlap(item, days) }
        .sortedWith(compareBy({ it.other.startTime }, { it.other.name }))
}

/**
 * How a group repeats, apart from its weekdays.
 *
 * Daily and weekly are one kind — seven weekly days *are* daily, which is what
 * [rulesForWeekdays] collapses them to — while a fortnightly or monthly rhythm
 * has to survive an edit that only moves the time.
 */
sealed interface Rhythm {
    data object Weekly : Rhythm
    data class Biweekly(val parity: WeekParity) : Rhythm
    data class Monthly(val weekOfMonth: Int) : Rhythm

    /** The rules this rhythm asks for on [weekdays], in week order. */
    fun rulesFor(weekdays: Set<DayOfWeek>): List<RecurrenceRule> = when (this) {
        Weekly -> rulesForWeekdays(weekdays)
        is Biweekly -> WEEK.filter { it in weekdays }.map { RecurrenceRule.Biweekly(it, parity) }
        is Monthly -> WEEK.filter { it in weekdays }.map { RecurrenceRule.Monthly(it, weekOfMonth) }
    }

    companion object {
        fun of(rule: RecurrenceRule): Rhythm = when (rule) {
            RecurrenceRule.Daily, is RecurrenceRule.Weekly -> Weekly
            is RecurrenceRule.Biweekly -> Biweekly(rule.parity)
            is RecurrenceRule.Monthly -> Monthly(rule.weekOfMonth)
        }
    }
}

/**
 * One standing task as the user thinks of it: "Arbeit, Mo–Fr, 9 bis 12:30".
 *
 * The model stores one definition per weekday, so that moving the Tuesday one
 * does not drag the Thursday one along. That is right for the schedule and wrong
 * for a list — the questionnaire's working week alone is ten rows of "Arbeit"
 * and five of "Pause". Definitions that agree on everything but the weekday are
 * shown, and edited, as one.
 */
data class RecurringGroup(
    val definitions: List<Item>,
) {
    val representative: Item get() = definitions.first()

    val rhythm: Rhythm get() = Rhythm.of(representative.recurrenceRule ?: RecurrenceRule.Daily)

    val weekdays: Set<DayOfWeek>
        get() = definitions.flatMap { it.recurrenceRule?.weekdaysOf().orEmpty() }.toSet()

    val ids: Set<String> get() = definitions.map { it.id }.toSet()
}

private data class GroupKey(
    val name: String,
    val category: Category?,
    val role: ItemRole?,
    val note: String?,
    val startTime: LocalTime?,
    val duration: Duration?,
    val travelBefore: Duration?,
    val returnAfter: Duration?,
    val breakAfter: Duration?,
    val endSound: Boolean,
    val rhythm: Rhythm,
    val repeatUntil: LocalDate?,
)

/**
 * The standing schedule, gathered into [RecurringGroup]s and ordered the way a
 * day runs: by start time, then name.
 *
 * Only what actually lays down occurrences — a bare recurring note still in the
 * Sammelliste is a note, and it is listed there.
 */
fun groupRecurring(definitions: List<Item>): List<RecurringGroup> =
    definitions
        .filter { it.completedAt == null && it.isConcretized }
        .filter { it.stage != Stage.COLLECTION && it.stage != Stage.LOCKED }
        .groupBy { item ->
            GroupKey(
                name = item.name,
                category = item.category,
                role = item.role,
                note = item.note,
                startTime = item.startTime,
                duration = item.estimatedDuration,
                travelBefore = item.travelBefore,
                returnAfter = item.returnAfter,
                breakAfter = item.breakAfter,
                endSound = item.endSound,
                rhythm = Rhythm.of(item.recurrenceRule!!),
                repeatUntil = item.repeatUntil,
            )
        }
        .values
        .map { members ->
            RecurringGroup(
                members.sortedBy { item ->
                    item.recurrenceRule?.weekdaysOf()?.minOfOrNull { WEEK.indexOf(it) } ?: 0
                },
            )
        }
        .sortedWith(compareBy({ it.representative.startTime }, { it.representative.name }))

/**
 * Which existing row each weekday of an edited group lands on.
 *
 * Reusing rows rather than replacing them keeps everything that points at an id —
 * a holiday rule, past completions — attached to the same task. A weekday that
 * had a row keeps it; a new weekday takes a row that is no longer needed, if
 * there is one, and only then gets a new id. [newId] is where fresh ids come from.
 *
 * Returns the rows to write, each with its rule, and the rows left over.
 */
fun reassignRows(
    existing: List<Item>,
    rules: List<RecurrenceRule>,
    newId: () -> String,
): Pair<List<Pair<String, RecurrenceRule>>, List<Item>> {
    val unused = existing.toMutableList()
    val assigned = mutableListOf<Pair<String, RecurrenceRule>?>()

    // First pass: rows that already sit on exactly this weekday.
    rules.forEach { rule ->
        val match = unused.firstOrNull { item ->
            val current = item.recurrenceRule ?: return@firstOrNull false
            if (rule is RecurrenceRule.Daily) {
                current is RecurrenceRule.Daily
            } else {
                current !is RecurrenceRule.Daily && current.weekdaysOf() == rule.weekdaysOf()
            }
        }
        if (match != null) unused.remove(match)
        assigned += match?.let { it.id to rule }
    }

    // Second pass: anything still unplaced takes a leftover row, or a new one.
    val result = rules.mapIndexed { index, rule ->
        assigned[index] ?: (unused.removeFirstOrNull()?.id ?: newId()) to rule
    }
    return result to unused
}
