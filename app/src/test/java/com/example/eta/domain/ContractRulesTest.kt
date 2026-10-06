package com.example.eta.domain

import com.example.eta.domain.contract.SlotStatus
import com.example.eta.domain.contract.contractsToCheck
import com.example.eta.domain.contract.freeSlots
import com.example.eta.domain.contract.rejectionReason
import com.example.eta.domain.contract.slotStatuses
import com.example.eta.domain.contract.upgradableContracts
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.ContractEffort
import com.example.eta.domain.model.ContractState
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContractRulesTest {

    private val now = Instant.parse("2026-09-01T20:00:00Z")
    private val signed = LocalDate(2026, 9, 1)

    private fun contract(
        slot: Int?,
        state: ContractState = ContractState.ACTIVE,
        signedOn: LocalDate = signed,
        endsOn: LocalDate = signed.plus(DatePeriod(days = 21)),
        effort: ContractEffort = ContractEffort.MITTEL,
        lastCheckedOn: LocalDate? = null,
    ) = Contract(
        slot = slot,
        title = "Kein Handy im Bett",
        conditions = "Handy bleibt draußen",
        breachDefinition = "Handy im Bett",
        effort = effort,
        signature = "P.",
        signedOn = signedOn,
        endsOn = endsOn,
        state = state,
        lastCheckedOn = lastCheckedOn,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `an active contract holds its slot`() {
        val statuses = slotStatuses(listOf(contract(slot = 1)), signed)
        assertTrue(statuses[0] is SlotStatus.Free)
        assertTrue(statuses[1] is SlotStatus.Taken)
        assertEquals(listOf(0, 2), freeSlots(listOf(contract(slot = 1)), signed))
    }

    @Test
    fun `a legacy contract holds no slot at all`() {
        val legacy = contract(slot = null, state = ContractState.LEGACY)
        assertEquals(listOf(0, 1, 2), freeSlots(listOf(legacy), signed))
    }

    @Test
    fun `a breach locks the slot for a month from signing, not from breaking`() {
        val broken = contract(slot = 0, state = ContractState.BROKEN)
        // Signed on 1 September, so the slot is blocked until 1 October, whenever
        // the breach happened.
        assertEquals(LocalDate(2026, 10, 1), broken.slotLockedUntil)

        val duringLock = slotStatuses(listOf(broken), LocalDate(2026, 9, 20))
        assertTrue(duringLock[0] is SlotStatus.Locked)

        val afterLock = slotStatuses(listOf(broken), LocalDate(2026, 10, 1))
        assertTrue(afterLock[0] is SlotStatus.Free)
    }

    @Test
    fun `the longest of several locks on one slot wins`() {
        val early = contract(slot = 0, state = ContractState.BROKEN, signedOn = LocalDate(2026, 8, 1))
        val late = contract(slot = 0, state = ContractState.BROKEN, signedOn = LocalDate(2026, 9, 1))
        val status = slotStatuses(listOf(early, late), LocalDate(2026, 9, 10))
        assertEquals(LocalDate(2026, 10, 1), (status[0] as SlotStatus.Locked).until)
    }

    @Test
    fun `a contract shorter than two weeks is refused`() {
        val reason = rejectionReason(
            contracts = emptyList(),
            slot = 0,
            signedOn = signed,
            endsOn = signed.plus(DatePeriod(days = 13)),
            conditions = "Etwas",
            signature = "P.",
            today = signed,
        )
        assertNotNull(reason)
        assertTrue(reason!!.contains("zwei Wochen"))
    }

    @Test
    fun `exactly two weeks is long enough`() {
        assertNull(
            rejectionReason(
                contracts = emptyList(),
                slot = 0,
                signedOn = signed,
                endsOn = signed.plus(DatePeriod(days = 14)),
                conditions = "Etwas",
                signature = "P.",
                today = signed,
            ),
        )
    }

    @Test
    fun `signing needs conditions and a signature`() {
        val term = signed.plus(DatePeriod(days = 21))
        assertNotNull(
            rejectionReason(emptyList(), 0, signed, term, conditions = " ", signature = "P.", today = signed),
        )
        assertNotNull(
            rejectionReason(emptyList(), 0, signed, term, conditions = "Etwas", signature = "", today = signed),
        )
    }

    @Test
    fun `a locked slot cannot be signed into`() {
        val broken = contract(slot = 0, state = ContractState.BROKEN)
        val reason = rejectionReason(
            contracts = listOf(broken),
            slot = 0,
            signedOn = LocalDate(2026, 9, 15),
            endsOn = LocalDate(2026, 10, 15),
            conditions = "Etwas",
            signature = "P.",
            today = LocalDate(2026, 9, 15),
        )
        assertNotNull(reason)
    }

    @Test
    fun `a month of uninterrupted running qualifies for legacy`() {
        val running = contract(slot = 0)
        assertFalse(running.canUpgradeToLegacy(LocalDate(2026, 9, 30)))
        assertTrue(running.canUpgradeToLegacy(LocalDate(2026, 10, 1)))
        assertEquals(
            listOf(running),
            upgradableContracts(listOf(running), LocalDate(2026, 10, 1)),
        )
    }

    @Test
    fun `a legacy contract pays a fifth`() {
        val legacy = contract(slot = null, state = ContractState.LEGACY, effort = ContractEffort.SCHWER)
        assertEquals(1.5, contract(slot = 0, effort = ContractEffort.SCHWER).dailyPayout, 0.0001)
        assertEquals(0.3, legacy.dailyPayout, 0.0001)
    }

    @Test
    fun `the evening asks each contract once a day`() {
        val unchecked = contract(slot = 0)
        val checked = contract(slot = 1, lastCheckedOn = LocalDate(2026, 9, 5))

        val due = contractsToCheck(listOf(unchecked, checked), LocalDate(2026, 9, 5))
        assertEquals(listOf(unchecked.id), due.map { it.id })
    }

    @Test
    fun `a contract is not asked about before it was signed`() {
        val future = contract(slot = 0, signedOn = LocalDate(2026, 9, 10))
        assertTrue(contractsToCheck(listOf(future), LocalDate(2026, 9, 5)).isEmpty())
    }

    @Test
    fun `broken contracts are never asked again`() {
        val broken = contract(slot = 0, state = ContractState.BROKEN)
        assertTrue(contractsToCheck(listOf(broken), signed).isEmpty())
    }

    @Test
    fun `a contract that ran its term is flagged as expiring`() {
        val running = contract(slot = 0, endsOn = LocalDate(2026, 9, 22))
        assertFalse(running.isExpiring(LocalDate(2026, 9, 21)))
        assertTrue(running.isExpiring(LocalDate(2026, 9, 22)))
    }
}
