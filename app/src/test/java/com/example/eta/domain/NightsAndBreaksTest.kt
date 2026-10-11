package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.data.local.Converters
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.NightOverride
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.BreakKind
import com.example.eta.domain.planning.breakKind
import com.example.eta.domain.planning.checkBreaks
import com.example.eta.domain.planning.nextWake
import com.example.eta.domain.planning.nightDateAt
import com.example.eta.domain.planning.sleepStretches
import com.example.eta.domain.setup.DEFAULT_WEEKEND
import com.example.eta.domain.setup.NightMode
import com.example.eta.domain.setup.NightTimes
import com.example.eta.domain.setup.UserSetup
import com.example.eta.domain.setup.WEEK
import com.example.eta.domain.setup.allNights
import com.example.eta.domain.setup.conflicts
import com.example.eta.domain.setup.movedTo
import com.example.eta.domain.setup.nightEndingOn
import com.example.eta.domain.setup.nightMode
import com.example.eta.domain.setup.recurringItems
import com.example.eta.domain.setup.rhythmDiverges
import com.example.eta.domain.setup.rhythmSpread
import com.example.eta.domain.setup.suggestedWeekendNight
import com.example.eta.domain.setup.withNightEndingOn
import com.example.eta.domain.setup.withNightMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 40: nights changed by hand, a night per weekday, and the Pausenchecker. */
class NightsAndBreaksTest {

    private val now = Instant.parse("2026-10-01T08:00:00Z")

    /** Bettfertig 22:00, sleep 23:00–07:00, every night. */
    private val draft = UserSetup.draft(now)

    // 13 October 2026 is a Tuesday.
    private val tuesday = LocalDate(2026, 10, 13)
    private val wednesday = LocalDate(2026, 10, 14)

    // --- a night moved ------------------------------------------------------

    @Test
    fun `winding down keeps its length and follows the bedtime`() {
        val night = NightTimes(LocalTime(22, 0), LocalTime(23, 0), LocalTime(7, 0))
        val later = night.movedTo(sleep = LocalTime(0, 30), wake = LocalTime(8, 0))

        assertEquals(LocalTime(23, 30), later.bedPrep)
        assertEquals(1.hours, later.bedPrepDuration())
        assertEquals(7.hours + 30.minutes, later.sleepDuration())
    }

    @Test
    fun `bedtimes either side of midnight are an hour apart, not twenty-three`() {
        val before = NightTimes(LocalTime(22, 30), LocalTime(23, 30), LocalTime(7, 0))
        val after = NightTimes(LocalTime(23, 30), LocalTime(0, 30), LocalTime(7, 0))

        assertEquals(60, rhythmSpread(listOf(before, after)))
        assertFalse(rhythmDiverges(listOf(before, after)))
    }

    @Test
    fun `more than an hour apart is said, exactly an hour is not`() {
        val usual = NightTimes(LocalTime(22, 0), LocalTime(23, 0), LocalTime(7, 0))

        assertFalse(rhythmDiverges(listOf(usual, usual.movedTo(LocalTime(23, 0), LocalTime(8, 0)))))
        assertTrue(rhythmDiverges(listOf(usual, usual.movedTo(LocalTime(23, 0), LocalTime(8, 15)))))
        assertTrue(rhythmDiverges(listOf(usual, usual.movedTo(LocalTime(21, 45), LocalTime(7, 0)))))
        assertFalse(rhythmDiverges(listOf(usual)))
    }

    @Test
    fun `the suggested weekend does not set off the advice`() {
        val withWeekend = draft.copy(weekendNight = draft.suggestedWeekendNight())
        assertFalse(withWeekend.rhythmDiverges())
        assertEquals(emptyList<Any>(), withWeekend.conflicts())
    }

    // --- how the nights are answered ----------------------------------------

    @Test
    fun `the three ways of answering are told apart`() {
        assertEquals(NightMode.SAME, draft.nightMode)
        assertEquals(NightMode.WEEKEND, draft.copy(weekendNight = draft.suggestedWeekendNight()).nightMode)
        assertEquals(NightMode.PER_DAY, draft.withNightMode(NightMode.PER_DAY).nightMode)
    }

    @Test
    fun `a weekend on other days, from before, reads as a night per weekday`() {
        val legacy = draft.copy(
            weekendNight = draft.suggestedWeekendNight(),
            weekendDays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
        )
        assertEquals(NightMode.PER_DAY, legacy.nightMode)
        // And still sleeps as it did.
        assertEquals(LocalTime(8, 0), legacy.nightEndingOn(DayOfWeek.MONDAY).wake)
        assertEquals(LocalTime(7, 0), legacy.nightEndingOn(DayOfWeek.SATURDAY).wake)
    }

    @Test
    fun `choosing a night per weekday moves nothing`() {
        val weekend = draft.copy(weekendNight = draft.suggestedWeekendNight())
        val perDay = weekend.withNightMode(NightMode.PER_DAY)

        assertEquals(weekend.allNights(), perDay.allNights())
        assertEquals(WEEK.toSet(), perDay.nightsByDay!!.keys)
    }

    @Test
    fun `going back to the weekend finds the weekend night again, on saturday and sunday`() {
        val weekendNight = NightTimes(LocalTime(23, 0), LocalTime(23, 45), LocalTime(8, 0))
        val perDay = draft.copy(weekendNight = weekendNight, weekendDays = setOf(DayOfWeek.MONDAY))
            .withNightMode(NightMode.PER_DAY)

        val back = perDay.withNightMode(NightMode.WEEKEND)
        assertEquals(NightMode.WEEKEND, back.nightMode)
        assertEquals(weekendNight, back.weekendNight)
        assertEquals(DEFAULT_WEEKEND, back.weekendDays)
        assertNull(back.nightsByDay)

        assertEquals(NightMode.SAME, back.withNightMode(NightMode.SAME).nightMode)
    }

    @Test
    fun `changing one weekday's night changes that one alone`() {
        val late = NightTimes(LocalTime(23, 0), LocalTime(0, 0), LocalTime(7, 30))
        val changed = draft.withNightEndingOn(DayOfWeek.TUESDAY, late)

        assertEquals(NightMode.PER_DAY, changed.nightMode)
        assertEquals(late, changed.nightEndingOn(DayOfWeek.TUESDAY))
        (WEEK - DayOfWeek.TUESDAY).forEach { day ->
            assertEquals(draft.nightEndingOn(day), changed.nightEndingOn(day))
        }
    }

    @Test
    fun `a night per weekday lays down one pair of tasks per night`() {
        val late = NightTimes(LocalTime(23, 0), LocalTime(0, 0), LocalTime(7, 30))
        val items = draft.withNightEndingOn(DayOfWeek.TUESDAY, late).recurringItems(now)

        assertTrue(items.none { it.id == "setup:bedprep" || it.id == "setup:morning" })
        val morning = items.single { it.id == "setup:morning-tuesday" }
        assertEquals(RecurrenceRule.Weekly(DayOfWeek.TUESDAY), morning.recurrenceRule)
        assertEquals(LocalTime(7, 30), morning.startTime)
        // Winding down for the night into Tuesday is on Monday evening.
        val bedPrep = items.single { it.id == "setup:bedprep-tuesday" }
        assertEquals(RecurrenceRule.Weekly(DayOfWeek.MONDAY), bedPrep.recurrenceRule)
        assertEquals(LocalTime(23, 0), bedPrep.startTime)
        assertEquals(7, items.count { it.id.startsWith("setup:morning-") })
    }

    @Test
    fun `seven nights survive the trip into their column and back`() {
        val converters = Converters()
        val nights = draft.withNightEndingOn(
            DayOfWeek.SUNDAY,
            NightTimes(LocalTime(23, 30), LocalTime(0, 30), LocalTime(9, 0)),
        ).nightsByDay

        assertEquals(nights, converters.stringToNightsByDay(converters.nightsByDayToString(nights)))
        assertNull(converters.stringToNightsByDay(converters.nightsByDayToString(null)))
    }

    // --- one night, for one date --------------------------------------------

    private val lateTuesday = mapOf(
        tuesday to NightOverride(tuesday, sleep = LocalTime(0, 30), wake = LocalTime(8, 30), updatedAt = now),
    )

    @Test
    fun `a one-off night is that date's and no other's`() {
        val night = draft.nightEndingOn(tuesday, lateTuesday)
        assertEquals(LocalTime(8, 30), night.wake)
        assertEquals(LocalTime(23, 30), night.bedPrep)

        assertEquals(draft.nightEndingOn(DayOfWeek.WEDNESDAY), draft.nightEndingOn(wednesday, lateTuesday))
        // A week later it is an ordinary Tuesday again.
        assertEquals(
            draft.nightEndingOn(DayOfWeek.TUESDAY),
            draft.nightEndingOn(LocalDate(2026, 10, 20), lateTuesday),
        )
    }

    @Test
    fun `the planner shades the night as it was changed`() {
        // Tuesday: asleep from 00:30 to 08:30, then the ordinary evening.
        assertEquals(
            listOf(30 until 510, 1380 until 1440),
            draft.sleepStretches(tuesday, lateTuesday),
        )
        // Monday: its own morning, and no evening stretch — bed is after midnight.
        assertEquals(
            listOf(0 until 420),
            draft.sleepStretches(LocalDate(2026, 10, 12), lateTuesday),
        )
        assertEquals(draft.sleepStretches(DayOfWeek.TUESDAY), draft.sleepStretches(tuesday, emptyMap()))
    }

    @Test
    fun `a press in the morning means last night, one in the evening the night to come`() {
        assertEquals(tuesday, draft.nightDateAt(tuesday, 3 * 60))
        assertEquals(wednesday, draft.nightDateAt(tuesday, 23 * 60 + 30))
        assertNull(draft.nightDateAt(tuesday, 12 * 60))
        // With the changed night, half past seven on Tuesday is still asleep.
        assertNull(draft.nightDateAt(tuesday, 7 * 60 + 30))
        assertEquals(tuesday, draft.nightDateAt(tuesday, 7 * 60 + 30, lateTuesday))
    }

    @Test
    fun `the wake alarm rings at the changed hour, once`() {
        val waking = draft.copy(wakeAlarm = true)

        assertEquals(
            LocalDateTime(2026, 10, 13, 8, 30),
            nextWake(waking, LocalDateTime(2026, 10, 12, 20, 0), lateTuesday),
        )
        assertEquals(
            LocalDateTime(2026, 10, 14, 7, 0),
            nextWake(waking, LocalDateTime(2026, 10, 13, 9, 0), lateTuesday),
        )
    }

    // --- the Pausenchecker --------------------------------------------------

    private fun entry(
        name: String,
        start: LocalTime,
        duration: Duration,
        category: Category? = Category.FOKUS,
        role: ItemRole? = null,
        type: ItemType = ItemType.TODO,
        pointsPerHour: Double? = null,
        breakAfter: Duration? = null,
        discardedAt: Instant? = null,
        completedAt: Instant? = null,
    ) = BlockWithItem(
        block = PlannedBlock(
            id = name,
            itemId = "item-$name",
            date = tuesday,
            start = start,
            plannedDuration = duration,
            breakAfter = breakAfter,
            origin = BlockOrigin.DRAGGED,
            discardedAt = discardedAt,
            completedAt = completedAt,
            createdAt = now,
            updatedAt = now,
        ),
        item = Item(
            id = "item-$name",
            type = type,
            name = name,
            stage = Stage.DAY,
            category = category,
            role = role,
            pointsPerHour = pointsPerHour,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private fun check(vararg blocks: BlockWithItem) = checkBreaks(blocks.toList(), 15.minutes, 3.hours)

    @Test
    fun `what counts as rest, as a task, and as neither`() {
        fun kind(category: Category? = Category.FOKUS, role: ItemRole? = null) =
            entry("x", LocalTime(9, 0), 1.hours, category = category, role = role).breakKind()

        assertEquals(BreakKind.TASK, kind())
        assertEquals(BreakKind.TASK, kind(role = ItemRole.WORK))
        assertEquals(BreakKind.TASK, kind(role = ItemRole.SPORT))
        assertEquals(BreakKind.REST, kind(category = null, role = ItemRole.BREAK))
        assertEquals(BreakKind.REST, kind(category = null, role = ItemRole.FREE_TIME))
        // Cooking pays as "Nebenbei" and is still the meal.
        assertEquals(BreakKind.REST, kind(category = Category.NEBENBEI, role = ItemRole.MEAL))
        assertEquals(BreakKind.REST, kind(category = Category.ACHTSAM))
        assertEquals(BreakKind.NEUTRAL, kind(category = null, role = ItemRole.MORNING))
        assertEquals(BreakKind.NEUTRAL, kind(category = null, role = ItemRole.BED_PREP))
        assertEquals(BreakKind.NEUTRAL, kind(category = null))
    }

    @Test
    fun `a custom spend is rest, a custom earn is work`() {
        fun spend(rate: Double) = entry(
            "p", LocalTime(9, 0), 1.hours, category = null, type = ItemType.SPEND, pointsPerHour = rate,
        ).breakKind()

        assertEquals(BreakKind.REST, spend(-5.0))
        assertEquals(BreakKind.REST, spend(0.0))
        assertEquals(BreakKind.TASK, spend(1.5))
    }

    @Test
    fun `every full two hours of tasks ask for a quarter of an hour`() {
        val short = check(entry("a", LocalTime(9, 0), 1.hours + 45.minutes))
        assertEquals(0, short.neededMinutes)
        assertFalse(short.tooFew)

        val long = check(
            entry("a", LocalTime(9, 0), 2.hours),
            entry("b", LocalTime(11, 0), 2.hours + 30.minutes),
        )
        assertEquals(270, long.taskMinutes)
        assertEquals(30, long.neededMinutes)
        assertTrue(long.tooFew)
    }

    @Test
    fun `breaks, the pause a task brings and free time all count`() {
        val day = check(
            entry("a", LocalTime(9, 0), 2.hours, breakAfter = 10.minutes),
            entry("pause", LocalTime(11, 15), 15.minutes, category = null, role = ItemRole.BREAK),
            entry("b", LocalTime(11, 30), 2.hours),
            entry("frei", LocalTime(20, 0), 1.hours, category = null, role = ItemRole.FREE_TIME),
        )
        assertEquals(240, day.taskMinutes)
        assertEquals(85, day.restMinutes)
        assertFalse(day.tooFew)
        assertTrue(day.longStretches.isEmpty())
    }

    @Test
    fun `more than three hours of tasks without rest is a long stretch`() {
        val day = check(
            entry("a", LocalTime(9, 0), 2.hours),
            entry("b", LocalTime(11, 0), 1.hours + 30.minutes),
            entry("pause", LocalTime(12, 30), 30.minutes, category = null, role = ItemRole.BREAK),
            entry("c", LocalTime(13, 0), 3.hours),
        )
        val stretch = day.longStretches.single()
        assertEquals(9 * 60, stretch.fromMinute)
        assertEquals(12 * 60 + 30, stretch.toMinute)
        assertEquals(210, stretch.taskMinutes)
    }

    @Test
    fun `before the first break and after the last one count as well`() {
        val day = check(
            entry("a", LocalTime(8, 0), 3.hours + 15.minutes),
            entry("pause", LocalTime(11, 15), 15.minutes, category = null, role = ItemRole.BREAK),
            entry("b", LocalTime(11, 30), 1.hours),
            entry("pause2", LocalTime(12, 30), 15.minutes, category = null, role = ItemRole.BREAK),
            entry("c", LocalTime(13, 0), 4.hours),
        )
        assertEquals(listOf(8 * 60, 13 * 60), day.longStretches.map { it.fromMinute })
    }

    @Test
    fun `a stretch is measured in task time, not on the clock`() {
        // Two hours in the morning and two in the evening, nothing in between:
        // nine hours apart, four hours of work.
        val apart = check(
            entry("a", LocalTime(9, 0), 1.hours + 30.minutes),
            entry("b", LocalTime(18, 0), 1.hours + 30.minutes),
        )
        assertTrue(apart.longStretches.isEmpty())

        // The morning routine neither adds to a stretch nor ends one.
        val framed = check(
            entry("a", LocalTime(7, 0), 2.hours),
            entry("morgen", LocalTime(9, 0), 45.minutes, category = null, role = ItemRole.MORNING),
            entry("b", LocalTime(10, 0), 1.hours + 30.minutes),
        )
        assertEquals(210, framed.longStretches.single().taskMinutes)
    }

    @Test
    fun `what was called off is not read, what is done still is`() {
        val day = check(
            entry("a", LocalTime(9, 0), 2.hours, completedAt = now),
            entry("b", LocalTime(11, 0), 2.hours, discardedAt = now),
        )
        assertEquals(120, day.taskMinutes)
        assertTrue(day.longStretches.isEmpty())
    }

    @Test
    fun `the limits are the user's`() {
        val blocks = listOf(entry("a", LocalTime(9, 0), 4.hours))

        val strict = checkBreaks(blocks, 30.minutes, 2.hours)
        assertEquals(60, strict.neededMinutes)
        assertEquals(1, strict.longStretches.size)

        val lax = checkBreaks(blocks, 5.minutes, 5.hours)
        assertEquals(10, lax.neededMinutes)
        assertTrue(lax.longStretches.isEmpty())
    }
}
