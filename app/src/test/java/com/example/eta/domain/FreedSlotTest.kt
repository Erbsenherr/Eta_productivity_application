package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.canPlace
import com.example.eta.domain.planning.claimsItsSlot
import com.example.eta.domain.planning.conflictsOf
import com.example.eta.domain.planning.firstFreeStart
import com.example.eta.domain.reevaluation.heldItsTime
import com.example.eta.domain.planning.occupiesTime
import com.example.eta.domain.planning.standing
import com.example.eta.domain.reevaluation.plannedMinutes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A task ticked off releases the stretch it was given — and that release must not
 * reach the accounting, or the user would be charged for hours they had earned.
 */
class FreedSlotTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    private fun entry(
        id: String,
        start: LocalTime,
        duration: Duration = 1.hours,
        travelBefore: Duration? = null,
        breakAfter: Duration? = null,
        completedAt: Instant? = null,
        discardedAt: Instant? = null,
        role: ItemRole? = null,
    ) = BlockWithItem(
        block = PlannedBlock(
            id = id,
            itemId = "item-$id",
            date = date,
            start = start,
            plannedDuration = duration,
            travelBefore = travelBefore,
            breakAfter = breakAfter,
            origin = BlockOrigin.DRAGGED,
            completedAt = completedAt,
            discardedAt = discardedAt,
            createdAt = now,
            updatedAt = now,
        ),
        item = Item(
            id = "item-$id",
            type = ItemType.TODO,
            name = id,
            stage = Stage.DAY,
            category = Category.FOKUS,
            role = role,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private fun candidate(start: LocalTime, duration: Duration = 30.minutes) = PlannedBlock(
        id = "new",
        itemId = "item-new",
        date = date,
        start = start,
        plannedDuration = duration,
        origin = BlockOrigin.DRAGGED,
        createdAt = now,
        updatedAt = now,
    )

    // ---- what each of the two questions answers ---------------------------

    @Test
    fun `an open block holds its slot and carries its time`() {
        val open = entry("open", LocalTime(14, 0))
        assertTrue(open.claimsItsSlot())
        assertTrue(open.occupiesTime())
    }

    @Test
    fun `a completed block releases its slot but still carries its time`() {
        val done = entry("done", LocalTime(14, 0), completedAt = now)
        assertFalse(done.claimsItsSlot())
        assertTrue(done.occupiesTime())
        assertTrue(done.heldItsTime())
    }

    @Test
    fun `a cancellation releases both`() {
        val off = entry("off", LocalTime(14, 0), discardedAt = now)
        assertFalse(off.claimsItsSlot())
        assertFalse(off.occupiesTime())
    }

    @Test
    fun `given-up free time keeps its slot, as it always did`() {
        val free = entry("free", LocalTime(14, 0), discardedAt = now, role = ItemRole.FREE_TIME)
        assertTrue(free.claimsItsSlot())
        assertTrue(free.occupiesTime())
    }

    @Test
    fun `completed free time releases its slot like anything else`() {
        val free = entry("free", LocalTime(14, 0), completedAt = now, role = ItemRole.FREE_TIME)
        assertFalse(free.claimsItsSlot())
        assertTrue(free.heldItsTime())
    }

    // ---- what placement sees ----------------------------------------------

    @Test
    fun `something new can be planned into a finished block's hour`() {
        val day = listOf(entry("done", LocalTime(14, 0), completedAt = now))
        assertTrue(canPlace(day.standing(), candidate(LocalTime(14, 0))))
    }

    @Test
    fun `an open block still refuses its hour`() {
        val day = listOf(entry("open", LocalTime(14, 0)))
        assertFalse(canPlace(day.standing(), candidate(LocalTime(14, 0))))
    }

    @Test
    fun `the whole container is freed, journey and break included`() {
        // 13:45 travel, 14:00-15:00 task, 15:00-15:25 break: the container runs
        // 13:45 to 15:25, and all of it comes back.
        val day = listOf(
            entry(
                "done",
                LocalTime(14, 0),
                travelBefore = 15.minutes,
                breakAfter = 25.minutes,
                completedAt = now,
            ),
        )
        assertEquals(
            13 * 60 + 45,
            firstFreeStart(day.standing(), 30.minutes, preferredStart = 13 * 60 + 45),
        )
    }

    @Test
    fun `a finished block collides with nothing`() {
        val day = listOf(
            entry("done", LocalTime(14, 0), completedAt = now),
            entry("open", LocalTime(14, 30)),
        )
        assertTrue(conflictsOf(day).isEmpty())
    }

    // ---- what the settlement sees -----------------------------------------

    @Test
    fun `leaving the freed stretch empty costs nothing`() {
        val day = listOf(entry("done", LocalTime(14, 0), completedAt = now))
        // The hour is still counted as carried, so the unplanned charge never
        // sees the gap the planner now shows.
        assertEquals(60, plannedMinutes(day.filter { it.heldItsTime() }))
    }

    @Test
    fun `filling the freed stretch and abandoning it costs nothing either`() {
        val done = entry("done", LocalTime(14, 0), completedAt = now)
        val abandoned = entry("new", LocalTime(14, 0), duration = 30.minutes)

        val withoutIt = plannedMinutes(listOf(done).filter { it.heldItsTime() })
        val withIt = plannedMinutes(listOf(done, abandoned).filter { it.heldItsTime() })
        assertEquals(withoutIt, withIt)
    }

    @Test
    fun `a task planned outside the freed stretch and abandoned still costs`() {
        val done = entry("done", LocalTime(14, 0), completedAt = now)
        val elsewhere = entry("later", LocalTime(17, 0))

        val held = plannedMinutes(listOf(done, elsewhere).filter { it.heldItsTime() })
        // Only the finished hour is held; the abandoned one at 17:00 is not, which
        // is the rule from step 10 and is untouched by any of this.
        assertEquals(60, held)
    }
}
