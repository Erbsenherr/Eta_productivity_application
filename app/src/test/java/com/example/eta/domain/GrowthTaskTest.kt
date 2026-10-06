package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.growth.GrowthFitProblem
import com.example.eta.domain.growth.GrowthSetting
import com.example.eta.domain.growth.MinuteSpan
import com.example.eta.domain.growth.firstFreeGrowthStart
import com.example.eta.domain.growth.growthTargetFit
import com.example.eta.domain.growth.grownDuration
import com.example.eta.domain.growth.isFullyGrown
import com.example.eta.domain.growth.isGrowthTask
import com.example.eta.domain.growth.placeDynamicGrowth
import com.example.eta.domain.growth.withGrowth
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.model.Stage
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthTaskTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    private fun definition(
        id: String,
        start: LocalTime,
        duration: kotlin.time.Duration,
        growth: GrowthSetting? = null,
        order: Int? = null,
        weekday: DayOfWeek = DayOfWeek.TUESDAY,
        breakAfter: kotlin.time.Duration? = null,
    ) = Item(
        id = id,
        type = ItemType.RECURRING,
        name = id,
        stage = Stage.DAY,
        category = Category.FOKUS,
        recurrenceRule = RecurrenceRule.Weekly(weekday),
        startTime = start,
        estimatedDuration = duration,
        breakAfter = breakAfter,
        growthOrder = order,
        createdAt = now,
        updatedAt = now,
    ).withGrowth(growth)

    private fun block(
        item: Item,
        start: LocalTime = item.startTime!!,
        duration: kotlin.time.Duration = item.estimatedDuration!!,
        completedAt: Instant? = null,
        discardedAt: Instant? = null,
    ) = BlockWithItem(
        block = PlannedBlock(
            id = "b-${item.id}",
            itemId = item.id,
            date = date,
            start = start,
            plannedDuration = duration,
            breakAfter = item.breakAfter,
            origin = BlockOrigin.RECURRING,
            completedAt = completedAt,
            discardedAt = discardedAt,
            createdAt = now,
            updatedAt = now,
        ),
        item = item,
    )

    // ---- the length, and how it moves -------------------------------------

    @Test
    fun `a definition with a target and an increment grows`() {
        val item = definition("sport", LocalTime(18, 0), 15.minutes, GrowthSetting(15.minutes, 1.hours, 5.minutes, false))
        assertTrue(item.isGrowthTask)
        assertEquals(15.minutes, item.estimatedDuration)
        assertEquals(20.minutes, item.grownDuration())
    }

    @Test
    fun `switching growth on starts at the start length`() {
        val plain = definition("sport", LocalTime(18, 0), 2.hours)
        val grown = plain.withGrowth(GrowthSetting(15.minutes, 1.hours, 5.minutes, false))
        assertEquals(15.minutes, grown.estimatedDuration)
    }

    @Test
    fun `an edit that leaves the start alone keeps where the task has got to`() {
        val setting = GrowthSetting(15.minutes, 1.hours, 5.minutes, false)
        val halfway = definition("sport", LocalTime(18, 0), 15.minutes, setting)
            .copy(estimatedDuration = 35.minutes)
        // Only the increment changed; the current length is not the form's to reset.
        val edited = halfway.withGrowth(setting.copy(increment = 10.minutes))
        assertEquals(35.minutes, edited.estimatedDuration)
    }

    @Test
    fun `the increment stops at the target`() {
        // Switching growth on puts the length at the start, so the task is moved
        // to where it has actually got to first.
        val item = definition("sport", LocalTime(18, 0), 15.minutes, GrowthSetting(15.minutes, 1.hours, 5.minutes, false))
            .copy(estimatedDuration = 58.minutes)
        assertEquals(1.hours, item.grownDuration())
        val done = item.copy(estimatedDuration = 1.hours)
        assertTrue(done.isFullyGrown)
        assertEquals(1.hours, done.grownDuration())
    }

    @Test
    fun `switching growth off leaves the length it reached`() {
        val item = definition("sport", LocalTime(18, 0), 15.minutes, GrowthSetting(15.minutes, 1.hours, 5.minutes, false))
            .copy(estimatedDuration = 40.minutes)
        val plain = item.withGrowth(null)
        assertFalse(plain.isGrowthTask)
        assertEquals(40.minutes, plain.estimatedDuration)
    }

    // ---- the free-slot search ---------------------------------------------

    @Test
    fun `a free earliest hour is taken as it stands`() {
        assertEquals(
            18 * 60,
            firstFreeGrowthStart(emptyList(), earliest = 18 * 60, length = 60),
        )
    }

    @Test
    fun `a taken hour chains onto the end of what is in the way, with no gap`() {
        val obstacle = MinuteSpan(18 * 60, 18 * 60 + 37)
        assertEquals(
            18 * 60 + 37,
            firstFreeGrowthStart(listOf(obstacle), earliest = 18 * 60, length = 30),
        )
    }

    @Test
    fun `the journey has to clear the obstacle too`() {
        val obstacle = MinuteSpan(18 * 60, 19 * 60)
        // Task at 19:15 so its 15 minutes of travel start at 19:00, which is free.
        assertEquals(
            19 * 60 + 15,
            firstFreeGrowthStart(listOf(obstacle), earliest = 18 * 60, length = 30, lead = 15),
        )
    }

    @Test
    fun `a day with no room left says so`() {
        val obstacle = MinuteSpan(0, 24 * 60)
        assertNull(firstFreeGrowthStart(listOf(obstacle), earliest = 0, length = 30))
    }

    // ---- placing a day ----------------------------------------------------

    @Test
    fun `two dynamic growth tasks chain in order of priority`() {
        val first = definition(
            "lesen", LocalTime(18, 0), 30.minutes,
            GrowthSetting(30.minutes, 1.hours, 5.minutes, dynamic = true), order = 0,
        )
        val second = definition(
            "sport", LocalTime(18, 0), 20.minutes,
            GrowthSetting(20.minutes, 1.hours, 5.minutes, dynamic = true), order = 1,
        )
        // Deliberately both stored at the same hour, which is what dynamic
        // placement is for.
        val moves = placeDynamicGrowth(listOf(block(second), block(first)))
            .associateBy { it.block.itemId }

        assertEquals(18 * 60, moves["lesen"]?.toMinute)
        assertEquals(18 * 60 + 30, moves["sport"]?.toMinute)
    }

    @Test
    fun `a static standing task pushes the growth task behind it`() {
        val fixed = definition("abendessen", LocalTime(18, 0), 45.minutes)
        val growth = definition(
            "sport", LocalTime(18, 0), 30.minutes,
            GrowthSetting(30.minutes, 1.hours, 5.minutes, dynamic = true), order = 0,
        )
        val moves = placeDynamicGrowth(listOf(block(fixed), block(growth)))
        assertEquals(1, moves.size)
        assertEquals(18 * 60 + 45, moves.first().toMinute)
    }

    @Test
    fun `a break is part of what the next one has to clear`() {
        val first = definition(
            "lesen", LocalTime(18, 0), 30.minutes,
            GrowthSetting(30.minutes, 1.hours, 5.minutes, dynamic = true),
            order = 0, breakAfter = 10.minutes,
        )
        val second = definition(
            "sport", LocalTime(18, 0), 20.minutes,
            GrowthSetting(20.minutes, 1.hours, 5.minutes, dynamic = true), order = 1,
        )
        val moves = placeDynamicGrowth(listOf(block(first), block(second)))
            .associateBy { it.block.itemId }
        assertEquals(18 * 60 + 40, moves["sport"]?.toMinute)
    }

    @Test
    fun `a completed occurrence is not moved, and releases its stretch`() {
        val done = definition(
            "lesen", LocalTime(18, 0), 30.minutes,
            GrowthSetting(30.minutes, 1.hours, 5.minutes, dynamic = true), order = 0,
        )
        val other = definition(
            "sport", LocalTime(18, 0), 20.minutes,
            GrowthSetting(20.minutes, 1.hours, 5.minutes, dynamic = true), order = 1,
        )
        val moves = placeDynamicGrowth(
            listOf(block(done, completedAt = now), block(other)),
        )
        // The finished one stays where it is — it is the record of what happened.
        assertEquals(listOf("sport"), moves.map { it.block.itemId })
        // And it is no longer in the way: a task that is over gives its stretch
        // back, so the next one takes the earliest hour rather than queueing
        // behind it. See `claimsItsSlot`.
        assertEquals(18 * 60, moves.first().toMinute)
    }

    @Test
    fun `a called-off occurrence blocks nothing`() {
        val cancelled = definition("abendessen", LocalTime(18, 0), 45.minutes)
        val growth = definition(
            "sport", LocalTime(18, 0), 30.minutes,
            GrowthSetting(30.minutes, 1.hours, 5.minutes, dynamic = true), order = 0,
        )
        val moves = placeDynamicGrowth(
            listOf(block(cancelled, discardedAt = now), block(growth)),
        )
        assertEquals(18 * 60, moves.first().toMinute)
    }

    @Test
    fun `a task without dynamic placement is left where it stands`() {
        val fixed = definition(
            "sport", LocalTime(18, 0), 30.minutes,
            GrowthSetting(30.minutes, 1.hours, 5.minutes, dynamic = false), order = 0,
        )
        assertTrue(placeDynamicGrowth(listOf(block(fixed))).isEmpty())
    }

    // ---- the warning at creation -------------------------------------------

    @Test
    fun `a target that still fits raises nothing`() {
        val issues = growthTargetFit(
            rules = listOf(RecurrenceRule.Weekly(DayOfWeek.TUESDAY)),
            earliestStart = 18 * 60,
            target = 1.hours,
            travelBefore = null,
            returnAfter = null,
            breakAfter = null,
            dynamic = true,
            order = 0,
            definitions = emptyList(),
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `an evening with no room for the final form warns`() {
        // The night starts at 20:00 here, in the shape of a standing task that
        // runs to midnight.
        val night = definition("schlafen", LocalTime(20, 0), 4.hours)
        val issues = growthTargetFit(
            rules = listOf(RecurrenceRule.Weekly(DayOfWeek.TUESDAY)),
            earliestStart = 19 * 60,
            target = 2.hours,
            travelBefore = null,
            returnAfter = null,
            breakAfter = null,
            dynamic = true,
            order = 0,
            definitions = listOf(night),
        )
        assertEquals(1, issues.size)
        assertEquals(GrowthFitProblem.NO_ROOM, issues.first().problem)
        assertEquals(DayOfWeek.TUESDAY, issues.first().weekday)
    }

    @Test
    fun `a fixed hour that something else will be standing in warns as a collision`() {
        val other = definition("abendessen", LocalTime(19, 0), 1.hours)
        val issues = growthTargetFit(
            rules = listOf(RecurrenceRule.Weekly(DayOfWeek.TUESDAY)),
            earliestStart = 18 * 60,
            target = 2.hours,
            travelBefore = null,
            returnAfter = null,
            breakAfter = null,
            dynamic = false,
            order = null,
            definitions = listOf(other),
        )
        assertEquals(GrowthFitProblem.COLLIDES, issues.single().problem)
    }

    @Test
    fun `the break counts towards whether the final form fits`() {
        val night = definition("schlafen", LocalTime(20, 0), 4.hours)
        val withoutBreak = growthTargetFit(
            rules = listOf(RecurrenceRule.Weekly(DayOfWeek.TUESDAY)),
            earliestStart = 19 * 60,
            target = 1.hours,
            travelBefore = null,
            returnAfter = null,
            breakAfter = null,
            dynamic = true,
            order = 0,
            definitions = listOf(night),
        )
        assertTrue(withoutBreak.isEmpty())

        val withBreak = growthTargetFit(
            rules = listOf(RecurrenceRule.Weekly(DayOfWeek.TUESDAY)),
            earliestStart = 19 * 60,
            target = 1.hours,
            travelBefore = null,
            returnAfter = null,
            breakAfter = 15.minutes,
            dynamic = true,
            order = 0,
            definitions = listOf(night),
        )
        assertEquals(GrowthFitProblem.NO_ROOM, withBreak.single().problem)
    }

    @Test
    fun `a higher-priority growth task takes the slot first`() {
        // 19:00 to 20:00 is all there is, and the other task is ahead in the tab.
        val night = definition("schlafen", LocalTime(20, 0), 4.hours)
        val rival = definition(
            "lesen", LocalTime(19, 0), 30.minutes,
            GrowthSetting(30.minutes, 1.hours, 5.minutes, dynamic = true), order = 0,
        )
        val issues = growthTargetFit(
            rules = listOf(RecurrenceRule.Weekly(DayOfWeek.TUESDAY)),
            earliestStart = 19 * 60,
            target = 1.hours,
            travelBefore = null,
            returnAfter = null,
            breakAfter = null,
            dynamic = true,
            order = 1,
            definitions = listOf(night, rival),
        )
        assertEquals(GrowthFitProblem.NO_ROOM, issues.single().problem)
    }

    @Test
    fun `an edited task does not collide with itself`() {
        val itself = definition(
            "sport", LocalTime(18, 0), 30.minutes,
            GrowthSetting(30.minutes, 1.hours, 5.minutes, dynamic = false), order = 0,
        )
        val issues = growthTargetFit(
            rules = listOf(RecurrenceRule.Weekly(DayOfWeek.TUESDAY)),
            earliestStart = 18 * 60,
            target = 1.hours,
            travelBefore = null,
            returnAfter = null,
            breakAfter = null,
            dynamic = false,
            order = 0,
            definitions = listOf(itself),
            ignoreIds = setOf("sport"),
        )
        assertTrue(issues.isEmpty())
    }
}
