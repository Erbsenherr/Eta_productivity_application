package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.SPONTANEOUS_BREAK
import com.example.eta.domain.planning.confirmedRetroactively
import com.example.eta.domain.planning.earlyBilling
import com.example.eta.domain.planning.nextAfterNow
import com.example.eta.domain.planning.pullForward
import com.example.eta.domain.planning.standing
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The question after a confirmation: is this tick about now, and what may be pulled
 * into the time it freed.
 */
class FollowUpTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    private fun entry(
        name: String,
        from: LocalTime,
        duration: Duration = 1.hours,
        completed: Boolean = false,
        discarded: Boolean = false,
        travel: Duration? = null,
    ): BlockWithItem {
        val item = Item.newRecurring(
            id = "item-$name",
            name = name,
            category = Category.FOKUS,
            recurrenceRule = RecurrenceRule.Daily,
            startTime = from,
            estimatedDuration = duration,
            now = now,
        )
        return BlockWithItem(
            block = PlannedBlock(
                id = "block-$name",
                itemId = item.id,
                date = date,
                start = from,
                plannedDuration = duration,
                origin = BlockOrigin.DRAGGED,
                travelBefore = travel,
                completedAt = if (completed) now else null,
                discardedAt = if (discarded) now else null,
                createdAt = now,
                updatedAt = now,
            ),
            item = item,
        )
    }

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun `an hour done in twenty minutes is billed forty`() {
        val billing = earlyBilling(entry("Arbeit", LocalTime(9, 0)).block, at(9, 20))!!

        assertEquals(20.minutes, billing.used)
        assertEquals(40.minutes, billing.full)
        assertTrue(billing.isCapped)
    }

    @Test
    fun `an hour done in forty minutes is billed whole`() {
        val billing = earlyBilling(entry("Arbeit", LocalTime(9, 0)).block, at(9, 40))!!

        assertEquals(1.hours, billing.full)
        assertFalse(billing.isCapped)
    }

    @Test
    fun `exactly half the time is where the cap stops biting`() {
        val billing = earlyBilling(entry("Arbeit", LocalTime(9, 0)).block, at(9, 30))!!

        assertEquals(1.hours, billing.full)
        assertFalse(billing.isCapped)
    }

    @Test
    fun `a tick at the planned end or after it is not early`() {
        val block = entry("Arbeit", LocalTime(9, 0)).block

        assertNull(earlyBilling(block, at(10, 0)))
        assertNull(earlyBilling(block, at(11, 30)))
    }

    @Test
    fun `a tick before the task began has no time used to measure`() {
        assertNull(earlyBilling(entry("Arbeit", LocalTime(9, 0)).block, at(8, 45)))
    }

    @Test
    fun `a length corrected by hand is what the whole task means`() {
        val block = entry("Arbeit", LocalTime(9, 0), duration = 2.hours).block
            .copy(actualDuration = 1.hours)

        assertEquals(40.minutes, earlyBilling(block, at(9, 20))!!.full)
        assertNull(earlyBilling(block, at(10, 0)))
    }

    @Test
    fun `ticking off punctually still asks about the next task`() {
        val done = entry("Arbeit", LocalTime(9, 0))
        val later = entry("Sport", LocalTime(14, 0))

        assertFalse(confirmedRetroactively(listOf(done, later), done.block, at(10, 2)))
    }

    @Test
    fun `a tick the day has moved past asks nothing`() {
        val morning = entry("Arbeit", LocalTime(9, 0))
        val noon = entry("Kochen", LocalTime(12, 0))

        // 14:00: the noon block has long begun, so confirming the morning one is a
        // statement about the past.
        assertTrue(confirmedRetroactively(listOf(morning, noon), morning.block, at(14)))
    }

    @Test
    fun `a cancelled block in between does not make a tick retroactive`() {
        val morning = entry("Arbeit", LocalTime(9, 0))
        val called = entry("Kochen", LocalTime(12, 0), discarded = true)

        assertFalse(confirmedRetroactively(listOf(morning, called), morning.block, at(14)))
    }

    @Test
    fun `the next task is the next one ahead of now, not the next in the plan`() {
        val blocks = listOf(
            entry("Vormittag", LocalTime(9, 0)),
            entry("Mittag", LocalTime(12, 0)),
            entry("Abend", LocalTime(18, 0)),
        )

        assertEquals("Mittag", nextAfterNow(blocks, at(10))?.item?.name)
        assertEquals("Abend", nextAfterNow(blocks, at(13))?.item?.name)
        assertNull(nextAfterNow(blocks, at(19)))
    }

    @Test
    fun `a journey counts, so the next task is due before its own start`() {
        val blocks = listOf(entry("Zahnarzt", LocalTime(17, 15), travel = 15.minutes))

        // At 17:05 the journey has already begun: nothing is "next" any more.
        assertNull(nextAfterNow(blocks, at(17, 5)))
        assertEquals("Zahnarzt", nextAfterNow(blocks, at(16, 59))?.item?.name)
    }

    @Test
    fun `a finished or cancelled block is never the next task`() {
        val blocks = listOf(
            entry("Erledigt", LocalTime(15, 0), completed = true),
            entry("Abgesagt", LocalTime(16, 0), discarded = true),
            entry("Offen", LocalTime(17, 0)),
        )

        assertEquals("Offen", nextAfterNow(blocks, at(14))?.item?.name)
    }

    @Test
    fun `pulling forward starts the task at the minute it was asked for`() {
        val next = entry("Sport", LocalTime(14, 0))
        val pull = pullForward(listOf(next).standing(), next.block, at(11, 23))

        assertEquals(at(11, 23), pull?.taskStart)
        assertNull(pull?.breakStart)
    }

    @Test
    fun `the journey is pulled forward with the task, not into the past`() {
        val next = entry("Zahnarzt", LocalTime(14, 0), travel = 15.minutes)
        val pull = pullForward(listOf(next).standing(), next.block, at(11, 0))

        // The container may start at 11:00, so the task itself starts a quarter of
        // an hour later.
        assertEquals(at(11, 15), pull?.taskStart)
    }

    @Test
    fun `it goes only as far as it fits`() {
        val other = entry("Termin", LocalTime(12, 0), duration = 1.hours)
        val next = entry("Sport", LocalTime(14, 0))

        val pull = pullForward(listOf(other, next).standing(), next.block, at(11, 30))

        // 11:30 would run into the 12:00 appointment, so the earliest place that
        // holds the whole hour is right after it — unsnapped.
        assertEquals(at(13, 0), pull?.taskStart)
    }

    @Test
    fun `nothing happens when the task cannot start any earlier`() {
        val next = entry("Sport", LocalTime(14, 0))

        assertNull(pullForward(listOf(next).standing(), next.block, at(14, 30)))
        assertNull(pullForward(listOf(next).standing(), next.block, at(14, 0)))
    }

    @Test
    fun `a break is laid down first and the task follows it`() {
        val next = entry("Sport", LocalTime(14, 0))

        val pull = pullForward(
            listOf(next).standing(),
            next.block,
            at(11, 0),
            withBreak = SPONTANEOUS_BREAK,
        )

        assertEquals(at(11, 0), pull?.breakStart)
        assertEquals(at(11, 15), pull?.taskStart)
    }

    @Test
    fun `no room for the break means no move at all`() {
        // Something stands exactly where the break would go.
        val other = entry("Termin", LocalTime(11, 0), duration = 30.minutes)
        val next = entry("Sport", LocalTime(14, 0))

        assertNull(
            pullForward(
                listOf(other, next).standing(),
                next.block,
                at(11, 5),
                withBreak = SPONTANEOUS_BREAK,
            ),
        )
    }
}
