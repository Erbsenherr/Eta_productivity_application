package com.example.eta.domain

import com.example.eta.data.local.BlockWithItem
import com.example.eta.domain.model.BlockOrigin
import com.example.eta.domain.model.Category
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.ContractEffort
import com.example.eta.domain.model.ContractState
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.PlannedBlock
import com.example.eta.domain.model.RecurrenceRule
import com.example.eta.domain.planning.cancellationCharged
import com.example.eta.domain.reevaluation.ContractVerdict
import com.example.eta.domain.reevaluation.LEGACY_DAILY_CAP
import com.example.eta.domain.reevaluation.plannedMinutes
import com.example.eta.domain.reevaluation.UNPLANNED_PENALTY_PER_HOUR
import com.example.eta.domain.reevaluation.settleDay
import com.example.eta.domain.reward.weeklyInflationOn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailySettlementTest {

    private val now = Instant.parse("2026-09-01T20:00:00Z")
    private val date = LocalDate(2026, 9, 1)

    /** Eight hours of night, so a bare day has sixteen available hours. */
    private val sleep = 8 * 60

    /** Two hours of daily free time in the setup — the ceiling on what a forfeit pays. */
    private val ALLOWANCE = 2 * 60

    private fun entry(
        id: String,
        from: LocalTime,
        duration: Duration,
        category: Category? = Category.FOKUS,
        completed: Boolean = false,
        discarded: Boolean = false,
        itemId: String = "item-$id",
        role: ItemRole? = null,
        excuse: String? = null,
        /** When the calling-off happened, if it is not to be "on the day itself". */
        discardedAt: Instant? = null,
    ): BlockWithItem {
        val item = Item.newRecurring(
            id = itemId,
            name = id,
            category = category,
            recurrenceRule = RecurrenceRule.Daily,
            startTime = from,
            estimatedDuration = duration,
            now = now,
            role = role,
        )
        return BlockWithItem(
            block = PlannedBlock(
                id = id,
                itemId = itemId,
                date = date,
                start = from,
                plannedDuration = duration,
                origin = BlockOrigin.RECURRING,
                completedAt = if (completed) now else null,
                discardedAt = discardedAt ?: if (discarded) now else null,
                forceMajeure = excuse,
                createdAt = now,
                updatedAt = now,
            ),
            item = item,
        )
    }

    private fun contract(
        state: ContractState,
        effort: ContractEffort = ContractEffort.MITTEL,
    ) = Contract(
        slot = if (state == ContractState.LEGACY) null else 0,
        title = "t",
        conditions = "c",
        breachDefinition = "b",
        effort = effort,
        signature = "P.",
        signedOn = date,
        endsOn = date,
        state = state,
        createdAt = now,
        updatedAt = now,
    )

    /**
     * A day with exactly fourteen hours planned *and done*, so nothing is charged
     * for. The blocks have to be completed: a slot that was dropped counts as
     * unplanned time, so a day of untouched blocks is the opposite of a full one.
     */
    private fun fullDay() = listOf(
        entry("a", LocalTime(7, 0), 7.hours, completed = true),
        entry("b", LocalTime(14, 0), 7.hours, completed = true),
    )

    @Test
    fun `overlapping blocks count once toward the planned time`() {
        val blocks = listOf(
            entry("a", LocalTime(9, 0), 2.hours),
            entry("b", LocalTime(10, 0), 2.hours),
        )
        assertEquals(3 * 60, plannedMinutes(blocks))
    }

    @Test
    fun `only completed blocks are harvested`() {
        val blocks = listOf(
            entry("a", LocalTime(9, 0), 2.hours, completed = true),
            entry("b", LocalTime(12, 0), 2.hours),
        )
        val settlement = settleDay(blocks, emptyList(), sleep, ALLOWANCE)
        assertEquals(2.0, settlement.harvest, 0.0001)
    }

    @Test
    fun `a kept contract pays its effort, a broken one pays nothing`() {
        val kept = contract(ContractState.ACTIVE, ContractEffort.SCHWER)
        val broken = contract(ContractState.ACTIVE, ContractEffort.LEICHT)

        val settlement = settleDay(
            blocks = fullDay(),
            verdicts = listOf(ContractVerdict(kept, true), ContractVerdict(broken, false)),
            sleepMinutes = sleep,
            freeTimeAllowanceMinutes = ALLOWANCE,
        )
        assertEquals(1.5, settlement.contractPayout, 0.0001)
    }

    @Test
    fun `legacy contracts are capped at two points a day and earn the crown`() {
        // Three heavy legacy contracts: 3 × 1.5 × 0.2 = 0.9, still under the cap.
        val modest = List(3) { ContractVerdict(contract(ContractState.LEGACY, ContractEffort.SCHWER), true) }
        val underCap = settleDay(fullDay(), modest, sleep, ALLOWANCE)
        assertEquals(0.9, underCap.legacyCredited, 0.0001)
        assertFalse(underCap.crowned)

        // Enough of them to break through it.
        val many = List(10) { ContractVerdict(contract(ContractState.LEGACY, ContractEffort.SCHWER), true) }
        val overCap = settleDay(fullDay(), many, sleep, ALLOWANCE)
        assertEquals(3.0, overCap.legacyGross, 0.0001)
        assertEquals(LEGACY_DAILY_CAP, overCap.legacyCredited, 0.0001)
        assertTrue(overCap.crowned)
    }

    @Test
    fun `giving up the free time block pays four points an hour`() {
        val blocks = fullDay() + entry(
            id = "freizeit",
            from = LocalTime(20, 0),
            duration = 2.hours,
            category = null,
            discarded = true,
            role = ItemRole.FREE_TIME,
        )
        val settlement = settleDay(blocks, emptyList(), sleep, ALLOWANCE)
        assertEquals(8.0, settlement.freeTimeForgone, 0.0001)
    }

    @Test
    fun `free time is recognised by its role, not by which row it came from`() {
        // A free-time block the user made by hand pays exactly like the generated one.
        val blocks = fullDay() + entry(
            id = "eigene-freizeit",
            from = LocalTime(20, 0),
            duration = 1.hours,
            discarded = true,
            itemId = "hand-made",
            role = ItemRole.FREE_TIME,
        )
        assertEquals(4.0, settleDay(blocks, emptyList(), sleep, ALLOWANCE).freeTimeForgone, 0.0001)
    }

    @Test
    fun `the forfeit pays at most the daily free time the setup provides`() {
        // Four hours of free time planned and dropped, but only two are covered.
        val blocks = fullDay() + entry(
            id = "viel-freizeit",
            from = LocalTime(18, 0),
            duration = 4.hours,
            discarded = true,
            role = ItemRole.FREE_TIME,
        )
        val settlement = settleDay(blocks, emptyList(), sleep, ALLOWANCE)
        assertEquals(8.0, settlement.freeTimeForgone, 0.0001)
    }

    @Test
    fun `several dropped free time blocks are capped together, not each`() {
        val blocks = fullDay() +
            entry("f1", LocalTime(17, 0), 2.hours, discarded = true, itemId = "f1", role = ItemRole.FREE_TIME) +
            entry("f2", LocalTime(19, 0), 2.hours, discarded = true, itemId = "f2", role = ItemRole.FREE_TIME)
        assertEquals(8.0, settleDay(blocks, emptyList(), sleep, ALLOWANCE).freeTimeForgone, 0.0001)
    }

    @Test
    fun `free time that was kept pays nothing`() {
        val blocks = fullDay() + entry(
            id = "freizeit",
            from = LocalTime(20, 0),
            duration = 2.hours,
            role = ItemRole.FREE_TIME,
        )
        assertEquals(0.0, settleDay(blocks, emptyList(), sleep, ALLOWANCE).freeTimeForgone, 0.0001)
    }

    @Test
    fun `a dropped block that is not the free time one pays nothing`() {
        val blocks = fullDay() + entry("sport", LocalTime(20, 0), 2.hours, discarded = true)
        assertEquals(0.0, settleDay(blocks, emptyList(), sleep, ALLOWANCE).freeTimeForgone, 0.0001)
    }

    @Test
    fun `two unplanned hours are free, the rest costs one and a half each`() {
        // Sixteen waking hours, twelve of them planned and done: four unplanned.
        val blocks = listOf(
            entry("a", LocalTime(7, 0), 6.hours, completed = true),
            entry("b", LocalTime(14, 0), 6.hours, completed = true),
        )
        val settlement = settleDay(blocks, emptyList(), sleep, ALLOWANCE)
        assertEquals(4.0, settlement.unplannedHours, 0.0001)
        assertEquals(3.0, settlement.unplannedPenalty, 0.0001)
    }

    @Test
    fun `a day planned to the hour costs nothing`() {
        val settlement = settleDay(fullDay(), emptyList(), sleep, ALLOWANCE)
        assertEquals(2.0, settlement.unplannedHours, 0.0001)
        assertEquals(0.0, settlement.unplannedPenalty, 0.0001)
    }

    @Test
    fun `sleep is never charged as unplanned time`() {
        val awake = settleDay(fullDay(), emptyList(), sleepMinutes = sleep, freeTimeAllowanceMinutes = ALLOWANCE)
        val sleepless = settleDay(fullDay(), emptyList(), sleepMinutes = 0, freeTimeAllowanceMinutes = ALLOWANCE)
        // The same plan leaves eight more hours unplanned without a night.
        assertEquals(8.0, sleepless.unplannedHours - awake.unplannedHours, 0.0001)
    }

    @Test
    fun `the total nets the penalty off the earnings`() {
        val blocks = listOf(entry("a", LocalTime(7, 0), 6.hours, completed = true))
        val settlement = settleDay(
            blocks = blocks,
            verdicts = listOf(ContractVerdict(contract(ContractState.ACTIVE), true)),
            sleepMinutes = sleep,
            freeTimeAllowanceMinutes = ALLOWANCE,
        )
        // 6 harvested + 1 contract − (10 − 2) × 1.5 unplanned.
        assertEquals(6.0 + 1.0 - 12.0, settlement.total, 0.0001)
    }

    @Test
    fun `an abandoned block costs its hours as unplanned time`() {
        // Fourteen hours planned, the second seven called off: those hours left
        // the plan and went by exactly as empty ones would have. `droppedHours`
        // is zero because nothing is still *promised* and unaccounted for — the
        // cancellation is its own line.
        val blocks = listOf(
            entry("a", LocalTime(7, 0), 7.hours, completed = true),
            entry("b", LocalTime(14, 0), 7.hours, discarded = true),
        )
        val settlement = settleDay(blocks, emptyList(), sleep, ALLOWANCE)
        assertEquals(9.0, settlement.unplannedHours, 0.0001)
        assertEquals(0.0, settlement.droppedHours, 0.0001)
        assertEquals(7.0 * UNPLANNED_PENALTY_PER_HOUR, settlement.unplannedPenalty, 0.0001)
    }

    @Test
    fun `a cancellation costs its hours at the configured rate`() {
        val blocks = listOf(entry("a", LocalTime(9, 0), 2.hours, discarded = true))
        val settlement = settleDay(blocks, emptyList(), sleep, ALLOWANCE, 1.0)

        assertEquals(2.0, settlement.cancelledHours, 0.0001)
        assertEquals(2.0, settlement.cancellationPenalty, 0.0001)
    }

    @Test
    fun `a quarter of an hour costs a quarter point`() {
        val blocks = listOf(entry("a", LocalTime(9, 0), 15.minutes, discarded = true))

        assertEquals(
            0.25,
            settleDay(blocks, emptyList(), sleep, ALLOWANCE, 1.0).cancellationPenalty,
            0.0001,
        )
    }

    @Test
    fun `the rate is a lever, and zero switches the charge off`() {
        val blocks = listOf(entry("a", LocalTime(9, 0), 2.hours, discarded = true))

        assertEquals(
            4.0,
            settleDay(blocks, emptyList(), sleep, ALLOWANCE, 2.0).cancellationPenalty,
            0.0001,
        )
        assertEquals(
            0.0,
            settleDay(blocks, emptyList(), sleep, ALLOWANCE, 0.0).cancellationPenalty,
            0.0001,
        )
    }

    @Test
    fun `higher force costs nothing`() {
        val blocks = listOf(
            entry("a", LocalTime(9, 0), 2.hours, discarded = true, excuse = "Praxis abgesagt"),
        )
        val settlement = settleDay(blocks, emptyList(), sleep, ALLOWANCE, 1.0)

        assertEquals(0.0, settlement.cancelledHours, 0.0001)
        assertEquals(0.0, settlement.cancellationPenalty, 0.0001)
    }

    @Test
    fun `the freed hours are ordinary hours, and filling them costs nothing`() {
        // The confirmed reading: a cancellation is charged for, and the hours it
        // gives back are charged again only if nothing takes them.
        val emptyAfterwards = listOf(
            entry("a", LocalTime(7, 0), 7.hours, completed = true),
            entry("b", LocalTime(14, 0), 7.hours, discarded = true),
        )
        val refilled = listOf(
            entry("a", LocalTime(7, 0), 7.hours, completed = true),
            entry("b", LocalTime(14, 0), 7.hours, discarded = true),
            entry("c", LocalTime(14, 0), 7.hours, completed = true, itemId = "item-c"),
        )

        val left = settleDay(emptyAfterwards, emptyList(), sleep, ALLOWANCE, 1.0)
        val filled = settleDay(refilled, emptyList(), sleep, ALLOWANCE, 1.0)

        // Both pay the same for the cancellation itself …
        assertEquals(left.cancellationPenalty, filled.cancellationPenalty, 0.0001)
        // … but only the day that left the hours empty pays for them a second time.
        assertTrue(left.unplannedPenalty > 0.0)
        assertEquals(0.0, filled.unplannedPenalty, 0.0001)
    }

    @Test
    fun `giving up free time is a forfeit, never a cancellation`() {
        // It pays four points an hour, in the user's favour. Charging it as a
        // cancellation as well would turn that payout into something smaller by a
        // side door — and its hours stay held, so no unplanned charge either.
        val blocks = listOf(
            entry("a", LocalTime(7, 0), 14.hours, completed = true),
            entry(
                "free",
                LocalTime(21, 0),
                2.hours,
                category = null,
                discarded = true,
                role = ItemRole.FREE_TIME,
            ),
        )
        val settlement = settleDay(blocks, emptyList(), sleep, ALLOWANCE, 1.0)

        assertEquals(0.0, settlement.cancellationPenalty, 0.0001)
        assertTrue(settlement.freeTimeForgone > 0.0)
        assertEquals(0.0, settlement.unplannedPenalty, 0.0001)
    }

    @Test
    fun `a block left unanswered costs the same as a dropped one`() {
        val dropped = listOf(
            entry("a", LocalTime(7, 0), 7.hours, completed = true),
            entry("b", LocalTime(14, 0), 7.hours, discarded = true),
        )
        val open = listOf(
            entry("a", LocalTime(7, 0), 7.hours, completed = true),
            entry("b", LocalTime(14, 0), 7.hours),
        )
        assertEquals(
            settleDay(dropped, emptyList(), sleep, ALLOWANCE).unplannedPenalty,
            settleDay(open, emptyList(), sleep, ALLOWANCE).unplannedPenalty,
            0.0001,
        )
    }

    @Test
    fun `refilling the freed slot makes the charge go away`() {
        // The 14:00 block was dropped, but something else ran in the same hours
        // and did happen. Planned time is a union of what stood, so nothing is
        // owed for that stretch.
        val blocks = listOf(
            entry("a", LocalTime(7, 0), 7.hours, completed = true),
            entry("b", LocalTime(14, 0), 7.hours, discarded = true, itemId = "b"),
            entry("ersatz", LocalTime(14, 0), 7.hours, completed = true, itemId = "ersatz"),
        )
        val settlement = settleDay(blocks, emptyList(), sleep, ALLOWANCE)
        assertEquals(0.0, settlement.droppedHours, 0.0001)
        assertEquals(2.0, settlement.unplannedHours, 0.0001)
        assertEquals(0.0, settlement.unplannedPenalty, 0.0001)
    }

    @Test
    fun `dropped free time is priced by the forfeit and not charged again`() {
        // Fourteen hours done plus two hours of free time given up: the forfeit
        // pays for those two, and they are not also billed as unplanned.
        val blocks = fullDay() + entry(
            id = "freizeit",
            from = LocalTime(21, 0),
            duration = 2.hours,
            category = null,
            discarded = true,
            role = ItemRole.FREE_TIME,
        )
        val settlement = settleDay(blocks, emptyList(), sleep, ALLOWANCE)
        assertEquals(8.0, settlement.freeTimeForgone, 0.0001)
        assertEquals(0.0, settlement.droppedHours, 0.0001)
        assertEquals(0.0, settlement.unplannedHours, 0.0001)
    }

    @Test
    fun `calling something off in advance costs nothing`() {
        // Deciding the evening before that tomorrow's Sport is not happening is
        // planning, not giving up: the hours go back into a day that has not
        // started and can still be filled. Only a day already confirmed and under
        // way can break a promise, and that is what the charge is for.
        val blocks = listOf(
            entry(
                "a",
                LocalTime(9, 0),
                2.hours,
                discardedAt = Instant.parse("2026-08-31T18:00:00Z"),
            ),
        )
        val settlement =
            settleDay(blocks, emptyList(), sleep, ALLOWANCE, 1.0, timeZone = TimeZone.UTC)

        assertEquals(0.0, settlement.cancelledHours, 0.0001)
        assertEquals(0.0, settlement.cancellationPenalty, 0.0001)
    }

    @Test
    fun `the same cancellation on the day itself does cost`() {
        val blocks = listOf(
            entry(
                "a",
                LocalTime(9, 0),
                2.hours,
                discardedAt = Instant.parse("2026-09-01T07:00:00Z"),
            ),
        )
        val settlement =
            settleDay(blocks, emptyList(), sleep, ALLOWANCE, 1.0, timeZone = TimeZone.UTC)

        assertEquals(2.0, settlement.cancelledHours, 0.0001)
        assertEquals(2.0, settlement.cancellationPenalty, 0.0001)
    }

    @Test
    fun `a cancellation in advance still leaves the plan`() {
        // Free is not the same as harmless. The hours come back, and if nothing
        // takes them they are charged as unplanned time like any other empty
        // stretch — which is exactly what makes the freed slot ordinary time.
        val blocks = listOf(
            entry(
                "a",
                LocalTime(9, 0),
                2.hours,
                discardedAt = Instant.parse("2026-08-31T18:00:00Z"),
            ),
        )
        val settlement =
            settleDay(blocks, emptyList(), sleep, ALLOWANCE, 1.0, timeZone = TimeZone.UTC)

        assertEquals(16.0, settlement.unplannedHours, 0.0001)
        assertEquals(0.0, settlement.droppedHours, 0.0001)
    }

    @Test
    fun `every way out of the charge is a different statement`() {
        val advance = entry(
            "advance",
            LocalTime(9, 0),
            1.hours,
            discardedAt = Instant.parse("2026-08-31T18:00:00Z"),
        )
        val excused = entry("excused", LocalTime(11, 0), 1.hours, discarded = true, excuse = "krank")
        val freeTime = entry(
            "frei",
            LocalTime(13, 0),
            1.hours,
            category = null,
            discarded = true,
            role = ItemRole.FREE_TIME,
        )
        val plain = entry("plain", LocalTime(15, 0), 1.hours, discarded = true)

        assertFalse(advance.cancellationCharged(TimeZone.UTC))
        assertFalse(excused.cancellationCharged(TimeZone.UTC))
        assertFalse(freeTime.cancellationCharged(TimeZone.UTC))
        assertTrue(plain.cancellationCharged(TimeZone.UTC))
    }

    @Test
    fun `inflation takes thirty percent of savings and leaves debt alone`() {
        assertEquals(3.0, weeklyInflationOn(10.0), 0.0001)
        assertEquals(0.0, weeklyInflationOn(-10.0), 0.0001)
        assertEquals(0.0, weeklyInflationOn(0.0), 0.0001)
    }
}
