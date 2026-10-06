package com.example.eta.domain

import com.example.eta.domain.contract.SlotStatus
import com.example.eta.domain.contract.abandonedContract
import com.example.eta.domain.contract.contractsToCheck
import com.example.eta.domain.contract.freeSlots
import com.example.eta.domain.contract.probationsAwaitingDecision
import com.example.eta.domain.contract.restartedContract
import com.example.eta.domain.contract.slotStatuses
import com.example.eta.domain.model.BREACH_LOCK
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.ContractEffort
import com.example.eta.domain.model.ContractState
import com.example.eta.domain.reevaluation.ContractVerdict
import com.example.eta.domain.reevaluation.settleDay
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

/**
 * A broken contract the user goes on serving.
 *
 * The rule in one line: the promise continues, the payment stops, and the slot
 * stays held until the user says what happens next.
 */
class ContractProbationTest {

    private val now = Instant.parse("2026-09-01T20:00:00Z")
    private val signed = LocalDate(2026, 9, 1)

    /** The day the month is up, counted from signing rather than from breaking. */
    private val lockEnds = signed.plus(BREACH_LOCK)

    private fun contract(
        slot: Int?,
        state: ContractState = ContractState.ACTIVE,
        signedOn: LocalDate = signed,
        endsOn: LocalDate = signedOn.plus(DatePeriod(days = 21)),
        lastCheckedOn: LocalDate? = null,
        editedAt: Instant? = null,
    ) = Contract(
        slot = slot,
        title = "Kein Handy im Bett",
        conditions = "Handy bleibt draußen",
        breachDefinition = "Handy im Bett",
        effort = ContractEffort.MITTEL,
        signature = "P.",
        signedOn = signedOn,
        endsOn = endsOn,
        state = state,
        lastCheckedOn = lastCheckedOn,
        editedAt = editedAt,
        createdAt = now,
        updatedAt = now,
    )

    private fun probation(slot: Int = 1) = contract(slot, state = ContractState.PROBATION)

    @Test
    fun `a served breach holds its slot and shows as such`() {
        val statuses = slotStatuses(listOf(probation()), signed)
        val serving = statuses[1]

        assertTrue(serving is SlotStatus.Serving)
        assertEquals(lockEnds, (serving as SlotStatus.Serving).until)
        assertFalse(serving.decisionDue)
        assertEquals(listOf(0, 2), freeSlots(listOf(probation()), signed))
    }

    @Test
    fun `the slot stays held after the month, because the decision is still owed`() {
        // The one thing that must not happen: the lock expires, the slot reads as
        // free, a new contract goes into it — and the old one is still being
        // answered for every evening. Two promises, one slot.
        val day = lockEnds.plus(DatePeriod(days = 3))
        val statuses = slotStatuses(listOf(probation()), day)

        assertTrue(statuses[1] is SlotStatus.Serving)
        assertTrue((statuses[1] as SlotStatus.Serving).decisionDue)
        assertFalse(1 in freeSlots(listOf(probation()), day))
    }

    @Test
    fun `an ordinary breach frees its slot once the month is up`() {
        val broken = contract(slot = 1, state = ContractState.BROKEN)

        assertTrue(slotStatuses(listOf(broken), signed)[1] is SlotStatus.Locked)
        assertTrue(slotStatuses(listOf(broken), lockEnds)[1] is SlotStatus.Free)
    }

    @Test
    fun `the evening still asks, and pays nothing for the answer`() {
        val served = probation()

        assertTrue(served.isRunning)
        assertEquals(listOf(served), contractsToCheck(listOf(served), signed))
        assertEquals(0.0, served.dailyPayout, 0.0001)

        // Kept, and still worth nothing: the settlement counts only ACTIVE and
        // LEGACY, so the exemption needs no special case of its own.
        val settlement = settleDay(
            blocks = emptyList(),
            verdicts = listOf(ContractVerdict(served, kept = true)),
            sleepMinutes = 8 * 60,
            freeTimeAllowanceMinutes = 0,
        )
        assertEquals(0.0, settlement.contractPayout, 0.0001)
        assertEquals(0.0, settlement.legacyGross, 0.0001)
    }

    @Test
    fun `the decision is offered only once the month is served`() {
        assertTrue(probationsAwaitingDecision(listOf(probation()), signed).isEmpty())
        assertEquals(1, probationsAwaitingDecision(listOf(probation()), lockEnds).size)

        // And neither answer may be taken early — the choice exists because the
        // consequence has been served.
        assertNull(restartedContract(probation(), signed, now))
        assertNull(abandonedContract(probation(), signed, now))
    }

    @Test
    fun `restarting keeps the words and puts the term back to zero`() {
        val restarted = restartedContract(probation(), lockEnds, now)

        assertNotNull(restarted)
        assertEquals(ContractState.ACTIVE, restarted!!.state)
        assertEquals("Kein Handy im Bett", restarted.title)
        assertEquals("Handy bleibt draußen", restarted.conditions)
        assertEquals(ContractEffort.MITTEL, restarted.effort)
        // Signed today, and running for as long as it originally did.
        assertEquals(lockEnds, restarted.signedOn)
        assertEquals(lockEnds.plus(DatePeriod(days = 21)), restarted.endsOn)
        // So it needs a full month again before it can become legacy.
        assertFalse(restarted.canUpgradeToLegacy(lockEnds))
        assertNull(restarted.closedAt)
        assertTrue(slotStatuses(listOf(restarted), lockEnds)[1] is SlotStatus.Taken)
    }

    @Test
    fun `restarting hands out no second wording change`() {
        // The one edit is an allowance per contract. Breaking it and taking it up
        // again must not be a way to get another.
        val edited = probation().copy(editedAt = now)
        val restarted = restartedContract(edited, lockEnds, now)

        assertNotNull(restarted!!.editedAt)
        assertFalse(restarted.isEditable)
    }

    @Test
    fun `giving it up makes it an ordinary broken contract and frees the slot`() {
        val abandoned = abandonedContract(probation(), lockEnds, now)

        assertNotNull(abandoned)
        assertEquals(ContractState.BROKEN, abandoned!!.state)
        assertNotNull(abandoned.closedAt)
        assertTrue(slotStatuses(listOf(abandoned), lockEnds)[1] is SlotStatus.Free)
        assertEquals(listOf(0, 1, 2), freeSlots(listOf(abandoned), lockEnds))
    }

    @Test
    fun `a served breach cannot be rewritten`() {
        assertFalse(probation().isEditable)
    }
}
