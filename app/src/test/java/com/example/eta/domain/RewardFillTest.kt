package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.ItemType
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.Reward
import com.example.eta.domain.model.Stage
import com.example.eta.domain.model.normalizeName
import com.example.eta.domain.reward.Earning
import com.example.eta.domain.reward.RewardTarget
import com.example.eta.domain.reward.earningsOf
import com.example.eta.domain.reward.pourInto
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Belohn-o-mat's arithmetic: what counts as earned, and where it goes.
 *
 * The rules are the user's — only position 1 is worked on, earned points stay
 * where they were poured, a binding narrows what counts — and each is easy to get
 * subtly wrong in a way no screen would show for weeks.
 */
class RewardFillTest {

    private val now = Instant.parse("2026-10-09T06:00:00Z")
    private val date = LocalDate(2026, 10, 9)

    private fun reward(
        id: String,
        cost: Double,
        progress: Double = 0.0,
        position: Int = 1,
        redeemedAt: Instant? = null,
    ) = Reward(
        id = id,
        name = id,
        cost = cost,
        progress = progress,
        position = position,
        redeemedAt = redeemedAt,
        createdAt = now,
        updatedAt = now,
    )

    private fun target(reward: Reward, vararg bound: String) =
        RewardTarget(reward, bound.map(::normalizeName).toSet())

    private fun earned(name: String, points: Double, standing: Boolean = false) =
        Earning(normalizeName(name), standing, points)

    private fun entry(
        name: String,
        type: ItemType = ItemType.TODO,
        duration: Duration = 1.hours,
        category: Category? = Category.FOKUS,
        completed: Boolean = true,
        discarded: Boolean = false,
        pointsPerHour: Double? = null,
        role: ItemRole? = null,
    ) = BlockWithItem(
        block = PlannedBlock(
            id = "block-$name",
            itemId = "item-$name",
            date = date,
            start = LocalTime(9, 0),
            plannedDuration = duration,
            origin = BlockOrigin.DRAGGED,
            completedAt = now.takeIf { completed },
            discardedAt = now.takeIf { discarded },
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

    @Test
    fun `only finished tasks earn, each with its own yield`() {
        val earnings = earningsOf(
            listOf(
                entry("Lernen", duration = 2.hours),
                entry("Aufräumen", category = Category.NEBENBEI),
                entry("Offen", completed = false),
            ),
        )

        assertEquals(listOf(2.0, 0.5), earnings.map { it.points })
    }

    @Test
    fun `free time given up is not something a task earned`() {
        // It pays four points an hour on the account, and nothing here: the
        // block was dropped, not finished.
        val forgone = entry(
            "Freizeit",
            type = ItemType.RECURRING,
            category = null,
            completed = false,
            discarded = true,
            role = ItemRole.FREE_TIME,
        )

        assertTrue(earningsOf(listOf(forgone)).isEmpty())
    }

    @Test
    fun `a custom spend is points spent, and takes nothing back out`() {
        val earnings = earningsOf(
            listOf(
                entry("Lernen", duration = 2.hours),
                entry("Serie", type = ItemType.SPEND, category = null, pointsPerHour = -5.0),
            ),
        )

        // Earned before anything is taken off: the two points stand.
        assertEquals(2.0, earnings.sumOf { it.points }, 0.0)
    }

    @Test
    fun `a standing task is told apart from a one-off`() {
        val earnings = earningsOf(
            listOf(entry("Sport", type = ItemType.RECURRING), entry("Steuer")),
        )

        assertEquals(listOf(true, false), earnings.map { it.standing })
    }

    @Test
    fun `everything goes to position one, and nothing further down`() {
        val first = reward("Steak", cost = 25.0, progress = 3.0)
        val second = reward("Urlaub", cost = 100.0, position = 2)

        val gains = pourInto(listOf(target(first), target(second)), listOf(earned("Lernen", 4.0)))

        assertEquals(1, gains.size)
        assertEquals(first.id, gains.single().reward.id)
        assertEquals(7.0, gains.single().after, 0.0)
        assertFalse(gains.single().completes)
    }

    @Test
    fun `position is the list order, not the row order`() {
        // The list is handed over in order; a reward that has earned more is
        // not preferred for it.
        val ahead = reward("Urlaub", cost = 100.0, progress = 60.0, position = 2)
        val first = reward("Steak", cost = 25.0, position = 1)

        val gains = pourInto(listOf(target(first), target(ahead)), listOf(earned("Lernen", 4.0)))

        assertEquals(first.id, gains.single().reward.id)
    }

    @Test
    fun `a bound reward counts its tasks and lets the rest go by`() {
        val bound = reward("Steak", cost = 25.0)
        val next = reward("Urlaub", cost = 100.0, position = 2)

        val gains = pourInto(
            listOf(target(bound, "Sport"), target(next)),
            listOf(earned("Sport", 1.0, standing = true), earned("Lernen", 4.0)),
        )

        // The four points of Lernen do not run on to the unbound reward below:
        // only position one is worked on.
        assertEquals(1, gains.size)
        assertEquals(1.0, gains.single().added, 0.0)
    }

    @Test
    fun `a binding means the standing task, not a one-off of the same name`() {
        val bound = reward("Steak", cost = 25.0)

        val gains = pourInto(
            listOf(target(bound, "Sport")),
            listOf(earned("Sport", 3.0, standing = false)),
        )

        assertTrue(gains.isEmpty())
    }

    @Test
    fun `a bound reward that saw none of its tasks holds the queue`() {
        val bound = reward("Steak", cost = 25.0)
        val next = reward("Urlaub", cost = 100.0, position = 2)

        val gains = pourInto(
            listOf(target(bound, "Sport"), target(next)),
            listOf(earned("Lernen", 4.0)),
        )

        assertTrue(gains.isEmpty())
    }

    @Test
    fun `what is left over once a reward is earned runs on into the next`() {
        val first = reward("Steak", cost = 25.0, progress = 22.0)
        val second = reward("Urlaub", cost = 100.0, progress = 10.0, position = 2)

        val gains = pourInto(listOf(target(first), target(second)), listOf(earned("Lernen", 5.0)))

        assertEquals(2, gains.size)
        assertTrue(gains[0].completes)
        assertEquals(25.0, gains[0].after, 1e-9)
        assertEquals(2.0, gains[1].added, 1e-9)
        assertEquals(12.0, gains[1].after, 1e-9)
    }

    @Test
    fun `the overflow passes the next reward's own binding`() {
        val first = reward("Steak", cost = 4.0)
        val second = reward("Urlaub", cost = 100.0, position = 2)

        // Eight points offered, four taken: half of each task is left over, and
        // of that only Sport's half is something the second reward counts.
        val gains = pourInto(
            listOf(target(first), target(second, "Sport")),
            listOf(earned("Sport", 2.0, standing = true), earned("Lernen", 6.0)),
        )

        assertEquals(4.0, gains[0].added, 1e-9)
        assertEquals(1.0, gains[1].added, 1e-9)
    }

    @Test
    fun `exactly enough earns it and leaves nothing to run on`() {
        val first = reward("Steak", cost = 5.0, progress = 2.0)
        val second = reward("Urlaub", cost = 100.0, position = 2)

        val gains = pourInto(listOf(target(first), target(second)), listOf(earned("Lernen", 3.0)))

        assertEquals(1, gains.size)
        assertTrue(gains.single().completes)
    }

    @Test
    fun `one evening can earn more than one reward`() {
        val a = reward("A", cost = 1.0)
        val b = reward("B", cost = 2.0, position = 2)
        val c = reward("C", cost = 10.0, position = 3)

        val gains = pourInto(
            listOf(target(a), target(b), target(c)),
            listOf(earned("Lernen", 5.0)),
        )

        assertEquals(listOf(1.0, 2.0, 2.0), gains.map { Math.round(it.added * 1e6) / 1e6 })
        assertEquals(listOf(true, true, false), gains.map { it.completes })
    }

    @Test
    fun `a reward earned in full or already taken is not in the queue`() {
        val full = reward("Steak", cost = 25.0, progress = 25.0)
        val taken = reward("Kino", cost = 10.0, progress = 10.0, position = 2, redeemedAt = now)
        val waiting = reward("Urlaub", cost = 100.0, position = 3)

        val gains = pourInto(
            listOf(target(full), target(taken), target(waiting)),
            listOf(earned("Lernen", 4.0)),
        )

        assertEquals(waiting.id, gains.single().reward.id)
        assertEquals(4.0, gains.single().added, 0.0)
    }

    @Test
    fun `no reward, or nothing earned, pours nothing`() {
        assertTrue(pourInto(emptyList(), listOf(earned("Lernen", 4.0))).isEmpty())
        assertTrue(pourInto(listOf(target(reward("Steak", cost = 25.0))), emptyList()).isEmpty())
    }

    @Test
    fun `a reward never holds more than it costs`() {
        val only = reward("Steak", cost = 5.0, progress = 4.0)

        val gain = pourInto(listOf(target(only)), listOf(earned("Lernen", 40.0))).single()

        assertEquals(1.0, gain.added, 1e-9)
        assertEquals(5.0, gain.after, 1e-9)
    }
}
