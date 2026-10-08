package com.example.eta.domain

import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.nextWake
import com.example.eta.domain.planning.sleepStretches
import com.example.eta.domain.setup.DEFAULT_WEEKEND
import com.example.eta.domain.setup.MealPlan
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.SetupLabels
import com.example.eta.domain.setup.SetupPart
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.setup.WorkSchedule
import com.example.eta.domain.setup.conflicts
import com.example.eta.domain.setup.freeMinutesPerWeek
import com.example.eta.domain.setup.isOwnedBySettings
import com.example.eta.domain.setup.recurringItems
import com.example.eta.domain.setup.skipping
import com.example.eta.domain.setup.sleepMinutesPerWeek
import com.example.eta.domain.setup.suggestedWeekendNight
import com.example.eta.domain.setup.wakeTimeOn
import com.example.eta.domain.setup.weeklySpans
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The weekend's own night, the skipped pages, and what the defaults promise. */
class WeekendNightTest {

    private val now = Instant.parse("2026-09-01T08:00:00Z")

    private val draft = UserSetup.draft(now)

    /** Weekdays 23:00–07:00, weekend 00:30–09:00 with winding down from 23:30. */
    private val lateWeekend = draft.copy(
        weekendNight = NightTimes(
            bedPrep = LocalTime(23, 30),
            sleep = LocalTime(0, 30),
            wake = LocalTime(9, 0),
        ),
    )

    @Test
    fun `answers that are simply accepted do not collide`() {
        assertEquals(emptyList<Any>(), draft.conflicts())
    }

    @Test
    fun `nor do they with the suggested weekend switched on`() {
        val withWeekend = draft.copy(weekendNight = draft.suggestedWeekendNight())
        assertEquals(emptyList<Any>(), withWeekend.conflicts())
    }

    @Test
    fun `a night round trips through its column`() {
        val night = lateWeekend.weekendNight!!
        assertEquals(night, NightTimes.decode(night.encode()))
    }

    @Test
    fun `without a weekend night nothing about the week changes`() {
        val plain = draft.copy(weekendNight = null)
        val sameTwice = draft.copy(weekendNight = plain.let { NightTimes(it.bedPrepTime, it.sleepTime, it.wakeTime) })
        assertEquals(plain.weeklySpans(), sameTwice.weeklySpans())
        assertEquals(plain.freeMinutesPerWeek(), sameTwice.freeMinutesPerWeek())
    }

    @Test
    fun `friday wakes as a weekday and goes to bed as a weekend`() {
        // Up at seven from the ordinary night; the weekend one starts after
        // midnight, so Friday itself has no evening stretch at all.
        assertEquals(listOf(0 until 420), lateWeekend.sleepStretches(DayOfWeek.FRIDAY))
    }

    @Test
    fun `saturday holds one whole weekend night and the start of none`() {
        // 00:30 to 09:00 — and the night into Sunday begins after midnight too.
        assertEquals(listOf(30 until 540), lateWeekend.sleepStretches(DayOfWeek.SATURDAY))
    }

    @Test
    fun `sunday sleeps in and then goes to bed for monday`() {
        assertEquals(
            listOf(30 until 540, 1380 until 1440),
            lateWeekend.sleepStretches(DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun `the longer weekend nights come out of the free hours`() {
        // Two nights of 8,5 h instead of 8 h; winding down stays an hour.
        assertEquals(draft.freeMinutesPerWeek() - 60, lateWeekend.freeMinutesPerWeek())
    }

    @Test
    fun `every night gets its own winding down and morning`() {
        val items = lateWeekend.recurringItems(now)
        val owned = items.filter { isOwnedBySettings(it.id) }

        assertEquals(14, owned.size)
        assertEquals(owned.size, owned.map { it.id }.toSet().size)
        assertTrue(owned.all { it.recurrenceRule is RecurrenceRule.Weekly })

        val saturdayMorning = owned.single { it.id == "setup:morning-saturday" }
        assertEquals(LocalTime(9, 0), saturdayMorning.startTime)
        assertEquals(RecurrenceRule.Weekly(DayOfWeek.SATURDAY), saturdayMorning.recurrenceRule)

        // The night that ends on Saturday winds down on Friday evening…
        val intoSaturday = owned.single { it.id == "setup:bedprep-saturday" }
        assertEquals(LocalTime(23, 30), intoSaturday.startTime)
        assertEquals(RecurrenceRule.Weekly(DayOfWeek.FRIDAY), intoSaturday.recurrenceRule)
        assertEquals(1.hours, intoSaturday.estimatedDuration)

        // …and the one that ends on Monday on Sunday evening, at the weekday hour.
        val intoMonday = owned.single { it.id == "setup:bedprep-monday" }
        assertEquals(LocalTime(22, 0), intoMonday.startTime)
        assertEquals(RecurrenceRule.Weekly(DayOfWeek.SUNDAY), intoMonday.recurrenceRule)
    }

    @Test
    fun `with every night alike the two stay single daily tasks`() {
        val owned = draft.recurringItems(now).filter { isOwnedBySettings(it.id) }
        assertEquals(setOf("setup:bedprep", "setup:morning"), owned.map { it.id }.toSet())
        assertTrue(owned.all { it.recurrenceRule == RecurrenceRule.Daily })
        assertFalse(isOwnedBySettings("setup:sport-tuesday"))
    }

    @Test
    fun `the wake alarm rings later on a saturday`() {
        val waking = lateWeekend.copy(wakeAlarm = true)
        // 2026-09-04 is a Friday.
        assertEquals(
            LocalDateTime(2026, 9, 5, 9, 0),
            nextWake(waking, LocalDateTime(2026, 9, 4, 12, 0)),
        )
        assertEquals(
            LocalDateTime(2026, 9, 7, 7, 0),
            nextWake(waking, LocalDateTime(2026, 9, 6, 9, 0)),
        )
        assertNull(nextWake(lateWeekend, LocalDateTime(2026, 9, 4, 12, 0)))
    }

    @Test
    fun `a skipped page lays nothing down and collides with nothing`() {
        val everything = draft.skipping(SetupPart.entries.toSet())
        val names = everything.recurringItems(now).map { it.name }.toSet()

        assertEquals(setOf(SetupLabels.BED_PREP, SetupLabels.MORNING), names)
        assertEquals(emptyList<Any>(), everything.conflicts())
        assertEquals(Duration.ZERO, everything.socialTimePerWeek)
        assertEquals(WorkSchedule.None, everything.work)
        assertEquals(MealPlan.DailyCooking(emptyList()), everything.meals)
    }

    @Test
    fun `skipping one page leaves the others as they were answered`() {
        val noSport = draft.skipping(setOf(SetupPart.SPORT))
        assertNull(noSport.sport)
        assertEquals(draft.copy(sport = null), noSport)
        assertEquals(draft, draft.skipping(emptySet()))
    }

    /** The reported case: to bed at one on Saturday night, which is Sunday. */
    private val oneOClock = draft.copy(
        weekendNight = NightTimes(
            bedPrep = LocalTime(0, 15),
            sleep = LocalTime(1, 0),
            wake = LocalTime(9, 0),
        ),
    )

    @Test
    fun `going to bed at one on saturday night lands on sunday`() {
        val night = oneOClock.weekendNight!!
        assertTrue(night.sleepsAfterMidnight)
        assertEquals(60, night.sleepOffset())
        assertEquals(15, night.bedPrepOffset())
        // Named by the day it falls on, which is the wake day itself.
        assertEquals(DEFAULT_WEEKEND, night.sleepDays(DEFAULT_WEEKEND))
        assertEquals(DEFAULT_WEEKEND, night.bedPrepDays(DEFAULT_WEEKEND))

        // Saturday evening stays free to the end; Sunday holds the whole night
        // and then the start of the ordinary one into Monday.
        assertEquals(listOf(60 until 540), oneOClock.sleepStretches(DayOfWeek.SATURDAY))
        assertEquals(
            listOf(60 until 540, 1380 until 1440),
            oneOClock.sleepStretches(DayOfWeek.SUNDAY),
        )
        assertEquals(emptyList<Any>(), oneOClock.conflicts())

        val intoSunday = oneOClock.recurringItems(now).single { it.id == "setup:bedprep-sunday" }
        assertEquals(RecurrenceRule.Weekly(DayOfWeek.SUNDAY), intoSunday.recurrenceRule)
        assertEquals(LocalTime(0, 15), intoSunday.startTime)
    }

    @Test
    fun `winding down before midnight and sleeping after it spans two days`() {
        val night = lateWeekend.weekendNight!!
        assertEquals(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), night.bedPrepDays(DEFAULT_WEEKEND))
        assertEquals(DEFAULT_WEEKEND, night.sleepDays(DEFAULT_WEEKEND))
    }

    @Test
    fun `the weekend is whichever days the user says`() {
        // Working Wednesday to Sunday: the weekend is Monday and Tuesday.
        val shifted = lateWeekend.copy(weekendDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))

        assertEquals(LocalTime(9, 0), shifted.wakeTimeOn(DayOfWeek.MONDAY))
        assertEquals(LocalTime(9, 0), shifted.wakeTimeOn(DayOfWeek.TUESDAY))
        assertEquals(LocalTime(7, 0), shifted.wakeTimeOn(DayOfWeek.SATURDAY))
        assertEquals(LocalTime(7, 0), shifted.wakeTimeOn(DayOfWeek.SUNDAY))

        // Saturday is an ordinary day again, Monday sleeps in.
        assertEquals(
            listOf(0 until 420, 1380 until 1440),
            shifted.sleepStretches(DayOfWeek.SATURDAY),
        )
        assertEquals(listOf(30 until 540), shifted.sleepStretches(DayOfWeek.MONDAY))

        val intoMonday = shifted.recurringItems(now).single { it.id == "setup:bedprep-monday" }
        assertEquals(RecurrenceRule.Weekly(DayOfWeek.SUNDAY), intoMonday.recurrenceRule)
        assertEquals(LocalTime(23, 30), intoMonday.startTime)

        // Still two long nights a week.
        assertEquals(lateWeekend.sleepMinutesPerWeek(), shifted.sleepMinutesPerWeek())
    }

    @Test
    fun `a single day can be the whole weekend`() {
        val onlySunday = lateWeekend.copy(weekendDays = setOf(DayOfWeek.SUNDAY))
        assertEquals(LocalTime(7, 0), onlySunday.wakeTimeOn(DayOfWeek.SATURDAY))
        assertEquals(LocalTime(9, 0), onlySunday.wakeTimeOn(DayOfWeek.SUNDAY))
        assertEquals(6 * 480 + 510, onlySunday.sleepMinutesPerWeek())
    }

    @Test
    fun `the chosen days mean nothing without a weekend night`() {
        val plain = draft.copy(weekendDays = setOf(DayOfWeek.MONDAY))
        assertEquals(draft.weeklySpans(), plain.weeklySpans())
        assertEquals(7 * 480, plain.sleepMinutesPerWeek())
    }

    @Test
    fun `a week has seven nights whatever the weekend does`() {
        val sleep = lateWeekend.weeklySpans().filter { it.label == SetupLabels.SLEEP }
        // 5 × 8 h and 2 × 8,5 h.
        assertEquals(5 * 480 + 2 * 510, sleep.sumOf { it.minutes })
        assertEquals(WEEK.toSet(), sleep.map { it.weekday }.toSet())
    }
}
