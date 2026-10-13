package com.example.eta.domain.setup

import com.example.eta.domain.planning.MINUTES_PER_DAY
import com.example.eta.domain.model.NightOverride
import com.example.eta.domain.planning.minuteOfDay
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
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

    /**
     * Whether the lights go out after midnight — on the wake day itself, with
     * the whole evening before it left free.
     */
    val sleepsAfterMidnight: Boolean get() = sleepOffset() >= 0

    /**
     * The weekdays going to bed falls on, for nights that end on [wakeDays]:
     * the day before each, or the wake day itself when it is after midnight.
     * What a form has to say next to the hour, since "01:00" alone does not
     * tell which day's one o'clock is meant.
     */
    fun sleepDays(wakeDays: Set<DayOfWeek>): Set<DayOfWeek> =
        wakeDays.map { it.shifted(Math.floorDiv(sleepOffset(), MINUTES_PER_DAY)) }.toSet()

    /** The same for winding down, which can start before midnight and end after. */
    fun bedPrepDays(wakeDays: Set<DayOfWeek>): Set<DayOfWeek> =
        wakeDays.map { it.shifted(Math.floorDiv(bedPrepOffset(), MINUTES_PER_DAY)) }.toSet()

    fun encode(): String =
        listOf(bedPrep, sleep, wake).joinToString(",") { it.toSecondOfDay().toString() }

    companion object {
        fun decode(value: String): NightTimes {
            val (bedPrep, sleep, wake) = value.split(',').map { LocalTime.fromSecondOfDay(it.toInt()) }
            return NightTimes(bedPrep, sleep, wake)
        }
    }
}

/**
 * The same night with other hours: winding down keeps its length and moves with
 * the bedtime. What every window that changes a night by hand writes — it asks
 * for two hours, and the third follows.
 */
fun NightTimes.movedTo(sleep: LocalTime, wake: LocalTime): NightTimes {
    val lead = bedPrepDuration().inWholeMinutes.toInt()
    val bedPrep = LocalTime.fromSecondOfDay(
        Math.floorMod(sleep.toSecondOfDay() - lead * 60, MINUTES_PER_DAY * 60),
    )
    return NightTimes(bedPrep = bedPrep, sleep = sleep, wake = wake)
}

/** How far apart two nights may lie before the app says something about it. */
const val RHYTHM_TOLERANCE_MINUTES = 60

/**
 * How far [nights] lie apart: the widest gap between two bedtimes or between
 * two hours of getting up, whichever is wider, in minutes.
 *
 * Bedtimes are compared as offsets from the wake day's midnight, so half past
 * eleven and half past midnight are an hour apart rather than twenty-three.
 */
fun rhythmSpread(nights: Collection<NightTimes>): Int {
    if (nights.size < 2) return 0
    val bedtimes = nights.map { it.sleepOffset() }
    val wakes = nights.map { it.wake.minuteOfDay() }
    return maxOf(bedtimes.max() - bedtimes.min(), wakes.max() - wakes.min())
}

/** Whether [nights] differ by **more** than an hour — the point the advice is given at. */
fun rhythmDiverges(nights: Collection<NightTimes>): Boolean =
    rhythmSpread(nights) > RHYTHM_TOLERANCE_MINUTES

/**
 * How the week's nights are answered.
 *
 * [SAME] is one night for every day, [WEEKEND] a second one for Friday and
 * Saturday evening, [PER_DAY] a night for each weekday — which the app offers
 * and advises against.
 */
enum class NightMode { SAME, WEEKEND, PER_DAY }

/**
 * Which of the three the answers amount to.
 *
 * A weekend on days other than Saturday and Sunday could be chosen between
 * steps 33 and 40. It no longer can, and what such a setup holds *is* a night
 * per weekday — so it is read as one, and its first edit writes it as one.
 */
val UserSetup.nightMode: NightMode
    get() = when {
        nightsByDay != null -> NightMode.PER_DAY
        weekendNight == null -> NightMode.SAME
        weekendDays != DEFAULT_WEEKEND -> NightMode.PER_DAY
        else -> NightMode.WEEKEND
    }

/** All seven nights, by the day each ends on. */
fun UserSetup.allNights(): Map<DayOfWeek, NightTimes> = WEEK.associateWith { nightEndingOn(it) }

/** Whether the week's nights lie more than an hour apart. */
fun UserSetup.rhythmDiverges(): Boolean = rhythmDiverges(allNights().values)

/**
 * The answers switched to [mode], keeping as much of what was answered as the
 * new mode can hold.
 *
 * Going to [NightMode.PER_DAY] starts from the nights as they are, so nothing
 * moves by choosing it. Leaving it drops the seven; a weekend night that stood
 * before is still there and comes back.
 */
fun UserSetup.withNightMode(mode: NightMode): UserSetup = when (mode) {
    NightMode.SAME -> copy(weekendNight = null, nightsByDay = null)
    NightMode.WEEKEND -> copy(
        nightsByDay = null,
        weekendNight = weekendNight ?: suggestedWeekendNight(),
        weekendDays = DEFAULT_WEEKEND,
    )
    NightMode.PER_DAY -> copy(nightsByDay = allNights())
}

/** The answers with the night that ends on [day] changed, and that one alone. */
fun UserSetup.withNightEndingOn(day: DayOfWeek, night: NightTimes): UserSetup =
    copy(nightsByDay = allNights() + (day to night))

/**
 * The night that ends on [date], a one-off change for that date included.
 *
 * What the day planner, the wake alarm and the evening's sums ask. The standing
 * week — the Wochenschema, the timetable's "Generell", the overlap warnings —
 * asks [nightEndingOn] by weekday and never sees an override.
 */
fun UserSetup.nightEndingOn(date: LocalDate, overrides: Map<LocalDate, NightOverride>): NightTimes {
    val usual = nightEndingOn(date.dayOfWeek)
    return overrides[date]?.let { usual.movedTo(it.sleep, it.wake) } ?: usual
}

/**
 * The days a weekend night ends on: Saturday and Sunday, so Friday and Saturday
 * evening. Fixed again since step 40 — a week that runs differently is answered
 * with a night per weekday.
 */
val DEFAULT_WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** [this] moved by [days], forwards or back, round the week. */
fun DayOfWeek.shifted(days: Int): DayOfWeek = WEEK[Math.floorMod(WEEK.indexOf(this) + days, WEEK.size)]

/** The night of an ordinary day — the three times the questionnaire always asks for. */
val UserSetup.weekdayNight: NightTimes get() = NightTimes(bedPrepTime, sleepTime, wakeTime)

/** The every-night answers, written back from a [NightTimes]. */
fun UserSetup.withWeekdayNight(night: NightTimes): UserSetup =
    copy(bedPrepTime = night.bedPrep, sleepTime = night.sleep, wakeTime = night.wake)

/**
 * Whether winding down begins after midnight — on the day of getting up.
 *
 * The counterpart of [NightTimes.sleepsAfterMidnight], and what the "Folgetag"
 * box beside that hour shows.
 */
val NightTimes.bedPrepIsNextDay: Boolean get() = bedPrepOffset() >= 0

/**
 * "Folgetag" ticked or unticked beside the **bedtime**.
 *
 * Which side of midnight an hour lies on was never stored and still is not: an
 * hour before getting up *is* after midnight, there being no other night it
 * could belong to. So the box cannot contradict the hour — it moves it. Ticked,
 * an evening bedtime becomes the same minute past twelve; unticked, one in the
 * small hours becomes the same minute past eleven. Winding down keeps its
 * length and follows, as it does wherever a bedtime is moved.
 */
fun NightTimes.withSleepNextDay(nextDay: Boolean): NightTimes {
    if (nextDay == sleepsAfterMidnight) return this
    val moved = if (nextDay) {
        // Past twelve, and still before getting up.
        LocalTime(0, sleep.minute).takeIf { it.minuteOfDay() < wake.minuteOfDay() } ?: LocalTime(0, 0)
    } else {
        LocalTime(23, sleep.minute)
    }
    return movedTo(moved, wake)
}

/**
 * The same for **winding down**.
 *
 * Ticked, it begins the same minute past twelve — and the bedtime, which cannot
 * come before it, follows across midnight at the old distance where it was
 * still in the evening. Unticked, it begins the same minute past eleven and the
 * bedtime stays where it is.
 */
fun NightTimes.withBedPrepNextDay(nextDay: Boolean): NightTimes {
    if (nextDay == bedPrepIsNextDay) return this
    if (!nextDay) return copy(bedPrep = LocalTime(23, bedPrep.minute))

    val start = LocalTime(0, bedPrep.minute)
    val keepsBedtime = sleepsAfterMidnight && sleep.minuteOfDay() > start.minuteOfDay()
    if (keepsBedtime) return copy(bedPrep = start)

    val lead = bedPrepDuration().inWholeMinutes.toInt().coerceIn(SNAP_LEAD_MINUTES, 2 * 60)
    val bedtime = (start.minuteOfDay() + lead).coerceAtMost(wake.minuteOfDay() - 1).coerceAtLeast(start.minuteOfDay())
    return copy(bedPrep = start, sleep = LocalTime(bedtime / 60, bedtime % 60))
}

/** The least winding down a bedtime moved across midnight is given. */
private const val SNAP_LEAD_MINUTES = 15

/**
 * The night that ends on [weekday]: its own where every weekday has one, else
 * the weekend's where one was given.
 */
fun UserSetup.nightEndingOn(weekday: DayOfWeek): NightTimes {
    nightsByDay?.let { return it[weekday] ?: weekdayNight }
    return weekendNight?.takeIf { weekday in weekendDays } ?: weekdayNight
}

fun UserSetup.wakeTimeOn(weekday: DayOfWeek): LocalTime = nightEndingOn(weekday).wake

/** All seven nights of a week together, each at its own length. */
fun UserSetup.sleepMinutesPerWeek(): Int =
    WEEK.sumOf { nightEndingOn(it).sleepDuration().inWholeMinutes.toInt() }

/**
 * What a weekend night is seeded with when the option is switched on: an hour
 * later, to bed and out of it. Derived from the weekday night so it moves with
 * whatever was already answered there — and exactly an hour, the most the app
 * does not advise against: a suggestion that set off its own warning would be
 * an odd thing to suggest. (It was two hours longer in bed until step 40.)
 */
fun UserSetup.suggestedWeekendNight(): NightTimes {
    fun LocalTime.plusMinutes(minutes: Int) =
        LocalTime.fromSecondOfDay(Math.floorMod(toSecondOfDay() + minutes * 60, 24 * 60 * 60))
    return NightTimes(
        bedPrep = bedPrepTime.plusMinutes(60),
        sleep = sleepTime.plusMinutes(60),
        wake = wakeTime.plusMinutes(60),
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
