package com.example.eta.domain.setup

import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.planning.minuteOfDay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/**
 * One night: winding down, lights out, getting up.
 *
 * A night **belongs to the day it ends on**. That is the only reading under which
 * "at the weekend" means what people mean by it: the nights that end on Saturday
 * and on Sunday morning — so going to bed late on Friday and Saturday, and
 * sleeping in on Saturday and Sunday, while Sunday evening already belongs to
 * Monday.
 *
 * The offsets are minutes from the **wake day's** midnight, negative for the
 * evening before. Everything that has to put a night on a calendar day works
 * from them, so "before or after midnight" is decided in one place.
 */
data class NightTimes(
    val bedPrep: LocalTime,
    val sleep: LocalTime,
    val wake: LocalTime,
) {
    /** When the lights go out: on the wake day itself if that is before getting up. */
    fun sleepOffset(): Int {
        val at = sleep.minuteOfDay()
        return if (at < wake.minuteOfDay()) at else at - MINUTES_PER_DAY
    }

    /** How long the night lasts, from falling asleep to getting up. */
    fun sleepDuration(): Duration = (wake.minuteOfDay() - sleepOffset()).minutes

    /** How long winding down lasts, from putting things away to lights out. */
    fun bedPrepDuration(): Duration {
        val from = bedPrep.minuteOfDay()
        val to = sleep.minuteOfDay()
        return (if (to >= from) to - from else to + MINUTES_PER_DAY - from).minutes
    }

    fun bedPrepOffset(): Int = sleepOffset() - bedPrepDuration().inWholeMinutes.toInt()

    fun encode(): String =
        listOf(bedPrep, sleep, wake).joinToString(",") { it.toSecondOfDay().toString() }

    companion object {
        fun decode(value: String): NightTimes {
            val (bedPrep, sleep, wake) = value.split(',').map { LocalTime.fromSecondOfDay(it.toInt()) }
            return NightTimes(bedPrep, sleep, wake)
        }
    }
}

/** The days a weekend night ends on. */
val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** [this] moved by [days], forwards or back, round the week. */
fun DayOfWeek.shifted(days: Int): DayOfWeek = WEEK[Math.floorMod(WEEK.indexOf(this) + days, WEEK.size)]

/** The night of an ordinary day — the three times the questionnaire always asks for. */
val UserSetup.weekdayNight: NightTimes get() = NightTimes(bedPrepTime, sleepTime, wakeTime)

/** The night that ends on [weekday]: the weekend's own where one was given. */
fun UserSetup.nightEndingOn(weekday: DayOfWeek): NightTimes =
    weekendNight?.takeIf { weekday in WEEKEND } ?: weekdayNight

fun UserSetup.wakeTimeOn(weekday: DayOfWeek): LocalTime = nightEndingOn(weekday).wake

/**
 * What a weekend night is seeded with when the option is switched on: an hour
 * later to bed and two hours longer in it. Derived from the weekday night so it
 * moves with whatever was already answered there.
 */
fun UserSetup.suggestedWeekendNight(): NightTimes {
    fun LocalTime.plusMinutes(minutes: Int) =
        LocalTime.fromSecondOfDay(Math.floorMod(toSecondOfDay() + minutes * 60, 24 * 60 * 60))
    return NightTimes(
        bedPrep = bedPrepTime.plusMinutes(60),
        sleep = sleepTime.plusMinutes(60),
        wake = wakeTime.plusMinutes(120),
    )
}

/**
 * The questions that may be left unanswered.
 *
 * Sleep and the planning times are not among them: the planner shades the night
 * and the two alarms need an hour, so the app cannot run without those.
 */
enum class SetupPart { MEALS, HOUSEKEEPING, SPORT, FREE_TIME, MINDFULNESS, WORK }

/**
 * The answers with [parts] taken out, so that nothing is laid down for them.
 *
 * Applied on the way out of the questionnaire rather than to its draft: what was
 * typed into a skipped page is still there if the user comes back to it.
 * A skipped free time takes the social budget with it — they are one page.
 */
fun UserSetup.skipping(parts: Set<SetupPart>): UserSetup = copy(
    meals = if (SetupPart.MEALS in parts) MealPlan.DailyCooking(emptyList()) else meals,
    housekeeping = housekeeping.takeUnless { SetupPart.HOUSEKEEPING in parts },
    sport = sport.takeUnless { SetupPart.SPORT in parts },
    freeTime = if (SetupPart.FREE_TIME in parts) freeTime.copy(duration = Duration.ZERO) else freeTime,
    socialTimePerWeek = if (SetupPart.FREE_TIME in parts) Duration.ZERO else socialTimePerWeek,
    mindfulness = mindfulness.takeUnless { SetupPart.MINDFULNESS in parts },
    work = if (SetupPart.WORK in parts) WorkSchedule.None else work,
)
