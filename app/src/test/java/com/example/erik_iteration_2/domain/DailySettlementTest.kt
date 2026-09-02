package com.example.erik_iteration_2.domain

import com.example.erik_iteration_2.data.local.BlockWithItem
import com.example.erik_iteration_2.domain.model.BlockOrigin
import com.example.erik_iteration_2.domain.model.Category
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.ContractEffort
import com.example.erik_iteration_2.domain.model.ContractState
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.ItemRole
import com.example.erik_iteration_2.domain.model.PlannedBlock
import com.example.erik_iteration_2.domain.model.RecurrenceRule
import com.example.erik_iteration_2.domain.reevaluation.ContractVerdict
import com.example.erik_iteration_2.domain.reevaluation.LEGACY_DAILY_CAP
import com.example.erik_iteration_2.domain.reevaluation.plannedMinutes
import com.example.erik_iteration_2.domain.reevaluation.settleDay
import com.example.erik_iteration_2.domain.reward.weeklyInflationOn
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
                discardedAt = if (discarded) now else null,
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

    /** A day with exactly fourteen planned hours, so nothing is charged for. */
    private fun fullDay() = listOf(
        entry("a", LocalTime(7, 0), 7.hours),
        entry("b", LocalTime(14, 0), 7.hours),
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
        // Sixteen waking hours, twelve of them planned: four unplanned.
        val blocks = listOf(
            entry("a", LocalTime(7, 0), 6.hours),
            entry("b", LocalTime(14, 0), 6.hours),
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
    fun `inflation takes thirty percent of savings and leaves debt alone`() {
        assertEquals(3.0, weeklyInflationOn(10.0), 0.0001)
        assertEquals(0.0, weeklyInflationOn(-10.0), 0.0001)
        assertEquals(0.0, weeklyInflationOn(0.0), 0.0001)
    }
}
