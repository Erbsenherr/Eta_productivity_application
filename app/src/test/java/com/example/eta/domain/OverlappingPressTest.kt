package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Stage
import com.example.eta.domain.planning.coveringMinute
import com.example.eta.domain.reward.yieldOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two things the last round turned into arithmetic: which blocks a long press
 * landed on, and what a card is worth before it has a block at all.
 */
class OverlappingPressTest {

    private val now = Instant.parse("2026-09-01T06:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    private fun entry(
        id: String,
        start: LocalTime,
        duration: Duration = 1.hours,
        travelBefore: Duration? = null,
    ) = BlockWithItem(
        block = PlannedBlock(
            id = id,
            itemId = "item-$id",
            date = date,
            start = start,
            plannedDuration = duration,
            travelBefore = travelBefore,
            origin = BlockOrigin.DRAGGED,
            createdAt = now,
            updatedAt = now,
        ),
        item = Item(
            id = "item-$id",
            type = ItemType.TODO,
            name = id,
            stage = Stage.DAY,
            category = Category.FOKUS,
            createdAt = now,
            updatedAt = now,
        ),
    )

    @Test
    fun `a minute only one block covers names that block alone`() {
        val blocks = listOf(entry("a", LocalTime(10, 0)), entry("b", LocalTime(12, 0)))

        assertEquals(listOf("a"), blocks.coveringMinute(10 * 60 + 30).map { it.block.id })
    }

    @Test
    fun `two blocks lying on top of each other are both named`() {
        val blocks = listOf(entry("a", LocalTime(10, 0)), entry("b", LocalTime(10, 0)))

        assertEquals(listOf("a", "b"), blocks.coveringMinute(10 * 60 + 5).map { it.block.id })
    }

    @Test
    fun `a partial overlap names both only inside the shared stretch`() {
        // 10:00–11:00 and 10:30–11:30.
        val blocks = listOf(
            entry("a", LocalTime(10, 0)),
            entry("b", LocalTime(10, 30)),
        )

        assertEquals(listOf("a"), blocks.coveringMinute(10 * 60 + 10).map { it.block.id })
        assertEquals(listOf("a", "b"), blocks.coveringMinute(10 * 60 + 45).map { it.block.id })
        assertEquals(listOf("b"), blocks.coveringMinute(11 * 60 + 10).map { it.block.id })
    }

    @Test
    fun `the end of a block is not part of it`() {
        val blocks = listOf(entry("a", LocalTime(10, 0)))

        assertTrue(blocks.coveringMinute(11 * 60).isEmpty())
    }

    @Test
    fun `a journey is not under the finger, because it carries no gesture`() {
        // The frame around the travel is drawn behind and has no long press of
        // its own, so only the task's own box counts as "under there".
        val blocks = listOf(entry("a", LocalTime(10, 0), travelBefore = 30.minutes))

        assertTrue(blocks.coveringMinute(9 * 60 + 45).isEmpty())
        assertEquals(listOf("a"), blocks.coveringMinute(10 * 60 + 1).map { it.block.id })
    }

    @Test
    fun `a block of no length still covers the minute it sits on`() {
        // What a card finished early with no duration to its name produces.
        val blocks = listOf(entry("a", LocalTime(10, 0), duration = Duration.ZERO))

        assertEquals(listOf("a"), blocks.coveringMinute(10 * 60).map { it.block.id })
    }

    @Test
    fun `what a card is worth is its hours times its category`() {
        assertEquals(1.5, yieldOf(Category.FOKUS, 90.minutes), 1e-9)
        assertEquals(0.75, yieldOf(Category.NEBENBEI, 90.minutes), 1e-9)
        assertEquals(1.5, yieldOf(Category.ACHTSAM, 90.minutes), 1e-9)
    }

    @Test
    fun `a card with no category is worth nothing`() {
        assertEquals(0.0, yieldOf(null, 3.hours), 1e-9)
    }
}
