package com.example.eta.domain.setup

import com.example.eta.domain.reevaluation.UNPLANNED_PENALTY_PER_HOUR
import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.WeekParity
import com.example.eta.domain.planning.DEFAULT_STILL_ACTIVE_PER_DAY
import com.example.eta.domain.planning.TaskAnnouncement
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** Weekdays in the order the questionnaire shows them. */
val WEEK: List<DayOfWeek> = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

val WORKDAYS: List<DayOfWeek> = WEEK.take(5)

private fun LocalTime.encode(): String = toSecondOfDay().toString()

private fun decodeTime(value: String): LocalTime = LocalTime.fromSecondOfDay(value.toInt())

/**
 * A fixed appointment on one or more weekdays — the shape almost every answer
 * takes.
 *
 * A set rather than a single day: sport twice a week is an ordinary thing to
 * want, and a one-day answer made the user create the same task twice.
 * [weekdays] is kept in [WEEK] order so the generated tasks come out predictable.
 */
data class WeeklySlot(
    val weekdays: Set<DayOfWeek>,
    val start: LocalTime,
    val duration: Duration,
) {
    constructor(weekday: DayOfWeek, start: LocalTime, duration: Duration) :
        this(setOf(weekday), start, duration)

    /** In the order the week runs, so ids and lists do not depend on set order. */
    val orderedWeekdays: List<DayOfWeek> get() = WEEK.filter { it in weekdays }

    fun encode(): String =
        orderedWeekdays.joinToString(",") { it.name } + "@${start.encode()}+${duration.inWholeSeconds}"

    companion object {
        fun decode(value: String): WeeklySlot {
            val (days, rest) = value.split('@', limit = 2)
            val (start, duration) = rest.split('+', limit = 2)
            return WeeklySlot(
                weekdays = days.split(',').filter { it.isNotEmpty() }.map(DayOfWeek::valueOf).toSet(),
                start = decodeTime(start),
                duration = duration.toLong().seconds,
            )
        }
    }
}

/** An appointment that repeats at the same time every day. */
data class DailySlot(
    val start: LocalTime,
    val duration: Duration,
) {
    fun encode(): String = "${start.encode()}+${duration.inWholeSeconds}"

    companion object {
        fun decode(value: String): DailySlot {
            val (start, duration) = value.split('+', limit = 2)
            return DailySlot(decodeTime(start), duration.toLong().seconds)
        }
    }
}

/** A stretch of a day with a start and an end, rather than a duration — work hours. */
data class TimeSpan(
    val start: LocalTime,
    val end: LocalTime,
) {
    val duration: Duration
        get() = (end.toSecondOfDay() - start.toSecondOfDay()).coerceAtLeast(0).seconds

    fun encode(): String = "${start.encode()}-${end.encode()}"

    companion object {
        fun decode(value: String): TimeSpan {
            val (start, end) = value.split('-', limit = 2)
            return TimeSpan(decodeTime(start), decodeTime(end))
        }
    }
}

enum class WorkSegmentKind { WORK, PAUSE }

/** A piece of a [WorkBlock] once its break has been cut out of it. */
data class WorkSegment(val kind: WorkSegmentKind, val span: TimeSpan)

/**
 * One stretch of work or university, with the break that sits inside it.
 *
 * The break is part of the work answer rather than a question of its own: a day
 * is made of the appointments one has, and a lunch break asked for separately
 * only ever collided with them.
 */
data class WorkBlock(
    val span: TimeSpan,
    val pause: TimeSpan? = null,
) {
    /**
     * Work, break, work — or just work when there is no break, or when the break
     * given does not lie inside the block. An unusable break is dropped rather
     * than clamped: moving someone's break for them would be worse than ignoring
     * it, and the questionnaire flags it while they are still looking at it.
     */
    fun segments(): List<WorkSegment> {
        val fits = pause != null &&
            pause.duration > Duration.ZERO &&
            pause.start >= span.start &&
            pause.end <= span.end

        val pieces = if (!fits) {
            listOf(WorkSegment(WorkSegmentKind.WORK, span))
        } else {
            listOf(
                WorkSegment(WorkSegmentKind.WORK, TimeSpan(span.start, pause!!.start)),
                WorkSegment(WorkSegmentKind.PAUSE, pause),
                WorkSegment(WorkSegmentKind.WORK, TimeSpan(pause.end, span.end)),
            )
        }
        return pieces.filter { it.span.duration > Duration.ZERO }
    }

    /** True when a break was given that [segments] will not use. */
    fun hasUnusablePause(): Boolean =
        pause != null && segments().none { it.kind == WorkSegmentKind.PAUSE }

    fun encode(): String = span.encode() + "~" + (pause?.encode() ?: "")

    companion object {
        fun decode(value: String): WorkBlock {
            val (span, pause) = value.split('~', limit = 2)
            return WorkBlock(
                span = TimeSpan.decode(span),
                pause = pause.takeIf { it.isNotEmpty() }?.let { TimeSpan.decode(it) },
            )
        }
    }
}

/** How the user handles food: one cooking day a week, or fixed times every day. */
sealed class MealPlan {

    data class MealPrep(val slot: WeeklySlot) : MealPlan()

    data class DailyCooking(val slots: List<DailySlot>) : MealPlan()

    fun encode(): String = when (this) {
        is MealPrep -> "P:${slot.encode()}"
        is DailyCooking -> "C:" + slots.joinToString("|") { it.encode() }
    }

    companion object {
        fun decode(value: String): MealPlan {
            val body = value.substring(2)
            return when (value.substringBefore(':')) {
                "P" -> MealPrep(WeeklySlot.decode(body))
                "C" -> DailyCooking(
                    body.split('|').filter { it.isNotEmpty() }.map(DailySlot::decode),
                )

                else -> error("Unknown meal plan: $value")
            }
        }
    }
}

/** Housekeeping, either every week or on every other week's parity. */
sealed class HousekeepingPlan {
    abstract val slot: WeeklySlot

    data class Weekly(override val slot: WeeklySlot) : HousekeepingPlan()

    data class Biweekly(
        override val slot: WeeklySlot,
        val parity: WeekParity,
    ) : HousekeepingPlan()

    /** One rule per chosen weekday; a set of days is a set of recurrences. */
    fun rulesFor(weekday: DayOfWeek): RecurrenceRule = when (this) {
        is Weekly -> RecurrenceRule.Weekly(weekday)
        is Biweekly -> RecurrenceRule.Biweekly(weekday, parity)
    }

    fun encode(): String = when (this) {
        is Weekly -> "W:${slot.encode()}"
        is Biweekly -> "B:${slot.encode()}:$parity"
    }

    companion object {
        fun decode(value: String): HousekeepingPlan {
            val parts = value.split(':')
            return when (parts[0]) {
                "W" -> Weekly(WeeklySlot.decode(parts[1]))
                "B" -> Biweekly(WeeklySlot.decode(parts[1]), WeekParity.valueOf(parts[2]))
                else -> error("Unknown housekeeping plan: $value")
            }
        }
    }
}

/** Self-mindfulness: a daily ritual or a weekly one. */
sealed class MindfulnessPlan {

    data class EveryDay(val slot: DailySlot) : MindfulnessPlan()

    data class Weekly(val slot: WeeklySlot) : MindfulnessPlan()

    fun encode(): String = when (this) {
        is EveryDay -> "D:${slot.encode()}"
        is Weekly -> "W:${slot.encode()}"
    }

    companion object {
        fun decode(value: String): MindfulnessPlan {
            val body = value.substring(2)
            return when (value.substringBefore(':')) {
                "D" -> EveryDay(DailySlot.decode(body))
                "W" -> Weekly(WeeklySlot.decode(body))
                else -> error("Unknown mindfulness plan: $value")
            }
        }
    }
}

/**
 * Work or university hours. The uniform case is the common one; [PerWeekday]
 * exists because the concept explicitly allows several separate stretches per day.
 */
sealed class WorkSchedule {

    data object None : WorkSchedule()

    /** The same hours on every Mo–Fr. */
    data class EveryWorkday(val block: WorkBlock) : WorkSchedule()

    data class PerWeekday(val blocks: Map<DayOfWeek, List<WorkBlock>>) : WorkSchedule()

    /** What is occupied on [weekday]; the uniform case expands to Mo–Fr here. */
    fun blocksOn(weekday: DayOfWeek): List<WorkBlock> = when (this) {
        is None -> emptyList()
        is EveryWorkday -> if (weekday in WORKDAYS) listOf(block) else emptyList()
        is PerWeekday -> blocks[weekday].orEmpty()
    }

    /** Work and break pieces of [weekday], in the order they occur. */
    fun segmentsOn(weekday: DayOfWeek): List<WorkSegment> =
        blocksOn(weekday).flatMap { it.segments() }

    fun encode(): String = when (this) {
        is None -> "N"
        is EveryWorkday -> "U:${block.encode()}"
        is PerWeekday -> "P:" + blocks
            .filterValues { it.isNotEmpty() }
            .entries
            .joinToString(";") { (day, list) ->
                "$day=" + list.joinToString(",") { it.encode() }
            }
    }

    companion object {
        fun decode(value: String): WorkSchedule = when (value.substringBefore(':')) {
            "N" -> None
            "U" -> EveryWorkday(WorkBlock.decode(value.substring(2)))
            "P" -> PerWeekday(
                value.substring(2)
                    .split(';')
                    .filter { it.isNotEmpty() }
                    .associate { entry ->
                        val (day, list) = entry.split('=', limit = 2)
                        DayOfWeek.valueOf(day) to list.split(',')
                            .filter { it.isNotEmpty() }
                            .map(WorkBlock::decode)
                    },
            )

            else -> error("Unknown work schedule: $value")
        }
    }
}

/** Single-row table: there is one user and one setup. */
const val SETUP_ID = 0

/** One point per called-off hour, which is the rate the rule was described with. */
const val DEFAULT_CANCELLATION_PENALTY = 1.0

/**
 * The answers to the setup questionnaire — the app's standing configuration.
 *
 * It is both the record of what the user said and the source the framework
 * recurring tasks are derived from (see `recurringItems`), so re-running the
 * questionnaire regenerates the schedule rather than layering a second one on top.
 *
 * Sleep is deliberately *not* one of those tasks: it is configuration the day
 * planner shades and the free-hour maths subtracts, not something to check off.
 */
@Entity(tableName = "user_setup")
data class UserSetup(
    @PrimaryKey val id: Int = SETUP_ID,
    /** Winding down starts here and ends at [sleepTime]; becomes a daily task. */
    val bedPrepTime: LocalTime,
    val sleepTime: LocalTime,
    val wakeTime: LocalTime,
    /**
     * Whether Eta itself rings at [wakeTime].
     *
     * Off by default: an app that starts waking someone because they answered
     * a questionnaire has overstepped. It hangs off [wakeTime] rather than
     * carrying a time of its own so the two can never disagree — the hour the
     * planner shades as the end of the night is the hour it rings at.
     */
    val wakeAlarm: Boolean = false,
    /**
     * The night at the weekend, where it differs — see [NightTimes] for which
     * nights those are. Null means every night is the same, which is what every
     * setup answered before this existed says.
     */
    val weekendNight: NightTimes? = null,
    /**
     * Which days count as the weekend: the days a [weekendNight] **ends** on, so
     * the evening before each belongs to it as well. Saturday and Sunday unless
     * the user's week runs differently — someone working Wednesday to Sunday has
     * their weekend on Monday and Tuesday. Means nothing without a [weekendNight].
     */
    val weekendDays: Set<DayOfWeek> = DEFAULT_WEEKEND,
    /** How much of the morning belongs to getting going, starting at [wakeTime]. */
    val morningDuration: Duration,
    val meals: MealPlan,
    val housekeeping: HousekeepingPlan?,
    val sport: WeeklySlot?,
    /** The daily minimum of unstructured time the concept insists on. */
    val freeTime: DailySlot,
    /** A weekly budget without a fixed time — subtracted from the free hours. */
    val socialTimePerWeek: Duration,
    val mindfulness: MindfulnessPlan?,
    val work: WorkSchedule,
    /** Drives the daily planning alarm. */
    val dailyPlanningTime: LocalTime,
    val weeklyPlanningDay: DayOfWeek,
    val weeklyPlanningTime: LocalTime,
    /**
     * The weekday the devaluation falls due on. Null follows the planning day,
     * which is what it always used to do and stays the sensible default.
     */
    val inflationDay: DayOfWeek? = null,
    /**
     * What one called-off hour costs, in points.
     *
     * Standing configuration rather than a constant, because it is the one lever
     * on how hard the app leans on a cancellation, and how hard that should be is
     * a matter for the person being leaned on. 1.0 is the rate the rule was
     * described with: a quarter of an hour costs 0.25, two hours cost 2.
     */
    val cancellationPenaltyPerHour: Double = DEFAULT_CANCELLATION_PENALTY,
    /**
     * Whether a day is charged for the hours nothing was planned into, and what
     * one such hour costs past the two that are free.
     *
     * Two answers rather than a rate that may be zero: switching the charge off
     * and on again should come back to the rate that was chosen, not to a
     * default. Both under the Punktetracker in the Advanced Features — the charge
     * is the points' firmest opinion about how a day should be spent, and that
     * opinion is the user's to hold or not.
     */
    val unplannedPenalty: Boolean = true,
    val unplannedPenaltyPerHour: Double = UNPLANNED_PENALTY_PER_HOUR,
    /**
     * The "Bin ich noch bei der Sache?" question during long tasks, and how many
     * times a day it is asked. Off by default, like every sound that was not
     * there before: nobody agreed to be asked by upgrading.
     */
    val stillActiveReminder: Boolean = false,
    val stillActivePerDay: Int = DEFAULT_STILL_ACTIVE_PER_DAY,
    /**
     * Whether a task event plays its sound or has the task's name read out,
     * and whether the notes are read with it. The sound is the default: it is
     * what the app did before the choice existed.
     */
    val taskAnnouncement: TaskAnnouncement = TaskAnnouncement.SOUND,
    val speakNotes: Boolean = false,
    /**
     * Whether the points system is **shown**. Off hides the account, the evening's
     * settlement page and every figure in points — and nothing else: the ledger
     * is written exactly as before, so switching it back on shows the balance as
     * if it had never been away. Read by the screens through `LocalPointsVisible`.
     *
     * The first of the four "Advanced Features", and the only one **on for a new
     * setup** — the user's later decision: the points are what the app's rules
     * are priced in, and the tutorial explains them. The other three stay off,
     * so a newcomer still finds few tabs.
     */
    val pointsSystem: Boolean = true,
    /**
     * Whether the Growth-Tasks tab and the Growth-Task extra are on show. Off
     * hides them; a task that already grows goes on growing.
     */
    val growthTasks: Boolean = false,
    /**
     * Whether the Verträge tab and the evening's contract question are on show.
     * Off hides both; a running contract then counts as kept, which is what a
     * question left unanswered always meant.
     */
    val contracts: Boolean = false,
    /** Whether the Belohn-o-mat tab and its evening page are on show. Rewards fill either way. */
    val rewards: Boolean = false,
    val completedAt: Instant,
    val updatedAt: Instant,
) {
    /** The weekday the devaluation is measured against. */
    @get:androidx.room3.Ignore
    val inflationWeekday: DayOfWeek get() = inflationDay ?: weeklyPlanningDay

    /** What an unplanned hour costs as things stand: nothing while the charge is off. */
    @get:Ignore
    val unplannedRate: Double get() = if (unplannedPenalty) unplannedPenaltyPerHour else 0.0

    companion object {
        /**
         * What the questionnaire opens with. Plausible enough that a user who
         * just taps through ends up with a usable schedule rather than an empty one.
         */
        fun draft(now: Instant) = UserSetup(
            bedPrepTime = LocalTime(22, 0),
            sleepTime = LocalTime(23, 0),
            wakeTime = LocalTime(7, 0),
            morningDuration = 45.minutes,
            meals = MealPlan.DailyCooking(listOf(DailySlot(LocalTime(18, 30), 1.hours))),
            housekeeping = HousekeepingPlan.Weekly(
                WeeklySlot(DayOfWeek.SATURDAY, LocalTime(10, 0), 1.hours),
            ),
            // After work and done before the cooking at half past six: answers
            // that are simply accepted must not collide with each other.
            sport = WeeklySlot(DayOfWeek.TUESDAY, LocalTime(17, 15), 1.hours),
            freeTime = DailySlot(LocalTime(20, 0), 1.hours + 30.minutes),
            // No longer asked or read; the column stays so no migration is needed.
            socialTimePerWeek = Duration.ZERO,
            mindfulness = MindfulnessPlan.EveryDay(DailySlot(LocalTime(21, 30), 15.minutes)),
            work = WorkSchedule.EveryWorkday(
                WorkBlock(
                    span = TimeSpan(LocalTime(9, 0), LocalTime(17, 0)),
                    pause = TimeSpan(LocalTime(12, 30), LocalTime(13, 15)),
                ),
            ),
            dailyPlanningTime = LocalTime(20, 0),
            weeklyPlanningDay = DayOfWeek.SUNDAY,
            weeklyPlanningTime = LocalTime(18, 0),
            completedAt = now,
            updatedAt = now,
        )
    }
}
