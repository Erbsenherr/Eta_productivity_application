package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.notesInOrder
import com.example.eta.domain.planning.nowAndNext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowAndNextTest {

    private val now = Instant.parse("2026-09-01T08:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    private fun entry(
        name: String,
        from: LocalTime,
        duration: Duration,
        completed: Boolean = false,
        discarded: Boolean = false,
        blockNote: String? = null,
        itemNote: String? = null,
    ): BlockWithItem {
        val item = Item.newRecurring(
            id = "item-$name",
            name = name,
            category = Category.FOKUS,
            recurrenceRule = RecurrenceRule.Daily,
            startTime = from,
            estimatedDuration = duration,
            now = now,
        ).copy(note = itemNote)

        return BlockWithItem(
            block = PlannedBlock(
                id = "block-$name",
                itemId = item.id,
                date = date,
                start = from,
                plannedDuration = duration,
                origin = BlockOrigin.RECURRING,
                completedAt = if (completed) now else null,
                discardedAt = if (discarded) now else null,
                note = blockNote,
                createdAt = now,
                updatedAt = now,
            ),
            item = item,
        )
    }

    private val day = listOf(
        entry("Frühstück", LocalTime(7, 0), 1.hours),
        entry("Arbeit", LocalTime(9, 0), 3.hours),
        entry("Sport", LocalTime(18, 0), 1.hours),
    )

    @Test
    fun `the block containing the moment is the current one`() {
        val result = nowAndNext(day, 10 * 60)
        assertEquals("Arbeit", result.current?.entry?.item?.name)
        assertEquals("Sport", result.next?.entry?.item?.name)
    }

    @Test
    fun `in a gap there is no current block, only a next one`() {
        val result = nowAndNext(day, 13 * 60)
        assertNull(result.current)
        assertEquals("Sport", result.next?.entry?.item?.name)
    }

    @Test
    fun `after the last block there is nothing left`() {
        val result = nowAndNext(day, 23 * 60)
        assertNull(result.current)
        assertNull(result.next)
    }

    @Test
    fun `a block ending exactly now is over`() {
        val result = nowAndNext(day, 8 * 60)
        assertNull(result.current)
        assertEquals("Arbeit", result.next?.entry?.item?.name)
    }

    @Test
    fun `something already ticked off is not what you are doing now`() {
        val blocks = listOf(entry("Arbeit", LocalTime(9, 0), 3.hours, completed = true)) +
            entry("Sport", LocalTime(18, 0), 1.hours)
        val result = nowAndNext(blocks, 10 * 60)

        assertNull(result.current)
        assertEquals("Sport", result.next?.entry?.item?.name)
    }

    @Test
    fun `a dropped occurrence is skipped too`() {
        val blocks = listOf(entry("Arbeit", LocalTime(9, 0), 3.hours, discarded = true))
        assertNull(nowAndNext(blocks, 10 * 60).current)
    }

    @Test
    fun `the note about today comes before the standing one`() {
        val card = entry(
            name = "Arbeit",
            from = LocalTime(9, 0),
            duration = 3.hours,
            blockNote = "heute im Homeoffice",
            itemNote = "Badge nicht vergessen",
        )
        assertEquals(listOf("heute im Homeoffice", "Badge nicht vergessen"), card.notesInOrder())
    }

    @Test
    fun `a card with only a standing note shows just that one`() {
        val card = entry("Arbeit", LocalTime(9, 0), 3.hours, itemNote = "Badge nicht vergessen")
        assertEquals(listOf("Badge nicht vergessen"), card.notesInOrder())
    }

    @Test
    fun `blank notes are dropped rather than shown as empty lines`() {
        val card = entry("Arbeit", LocalTime(9, 0), 3.hours, blockNote = "   ", itemNote = null)
        assertEquals(emptyList<String>(), card.notesInOrder())
    }
}
