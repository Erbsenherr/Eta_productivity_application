package com.example.eta.domain

import com.example.eta.domain.contract.probationAnswered
import com.example.eta.domain.contract.restartedContract
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.ContractEffort
import com.example.eta.domain.model.ContractState
import com.example.eta.domain.model.REINSTATEMENT_STREAK
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
 * A broken contract earning its way back: two weeks kept without a gap while it
 * is being served out, and it may be put into force again before the lock ends.
 */
class ContractReinstatementTest {

    private val now = Instant.parse("2026-09-01T20:00:00Z")
    private val signed = LocalDate(2026, 9, 1)

    /** Broken on the evening of the 2nd and carried on. */
    private val broken = Contract(
        slot = 0,
        title = "Kein Handy im Bett",
        conditions = "Handy bleibt vor der Tür",
        breachDefinition = "Handy im Bett",
        effort = ContractEffort.MITTEL,
        signature = "P.",
        signedOn = signed,
        endsOn = signed.plus(DatePeriod(days = 21)),
        state = ContractState.PROBATION,
        lastCheckedOn = LocalDate(2026, 9, 2),
        createdAt = now,
        updatedAt = now,
    )

    private fun day(n: Int) = LocalDate(2026, 9, 2).plus(DatePeriod(days = n))

    /** [contract] answered "gehalten" on every one of [days]. */
    private fun kept(contract: Contract, days: Iterable<Int>): Contract =
        days.fold(contract) { c, n -> probationAnswered(c, kept = true, date = day(n), now = now) }

    @Test
    fun `a fresh probation has no run`() {
        assertEquals(0, broken.probationStreak(day(0)))
        assertFalse(broken.canBeReinstated(day(0)))
    }

    @Test
    fun `every kept evening adds a day`() {
        val served = kept(broken, 1..5)
        assertEquals(day(1), served.probationKeptSince)
        assertEquals(5, served.probationStreak(day(5)))
        // And it still stands the next morning, before that evening's answer.
        assertEquals(5, served.probationStreak(day(6)))
        assertEquals(ContractState.PROBATION, served.state)
    }

    @Test
    fun `fourteen in a row lets it back in, thirteen do not`() {
        val thirteen = kept(broken, 1..13)
        assertFalse(thirteen.canBeReinstated(day(13)))
        assertNull(restartedContract(thirteen, day(13), now))

        val fourteen = kept(broken, 1..REINSTATEMENT_STREAK)
        val today = day(REINSTATEMENT_STREAK)
        assertTrue(fourteen.canBeReinstated(today))

        // Well before the month's lock is up — that is the reward.
        assertFalse(fourteen.probationDecisionDue(today))
        val back = restartedContract(fourteen, today, now)
        assertNotNull(back)
        assertEquals(ContractState.ACTIVE, back!!.state)
        assertNull(back.slotLockedUntil)
        assertEquals(1.0, back.dailyPayout, 0.0)
    }

    @Test
    fun `reinstating puts the term back to zero`() {
        val today = day(REINSTATEMENT_STREAK)
        val back = restartedContract(kept(broken, 1..REINSTATEMENT_STREAK), today, now)!!

        assertEquals(today, back.signedOn)
        assertEquals(today.plus(DatePeriod(days = 21)), back.endsOn)
        assertNull(back.probationKeptSince)
        // A full month again before it can become legacy.
        assertFalse(back.canUpgradeToLegacy(today.plus(DatePeriod(days = 29))))
        assertTrue(back.canUpgradeToLegacy(today.plus(DatePeriod(months = 1))))
    }

    @Test
    fun `an evening not kept ends the run`() {
        val slipped = probationAnswered(kept(broken, 1..10), kept = false, date = day(11), now = now)
        assertNull(slipped.probationKeptSince)
        assertEquals(0, slipped.probationStreak(day(11)))
        assertEquals(ContractState.PROBATION, slipped.state)

        // The count starts over rather than carrying on from ten.
        val again = kept(slipped, 12..14)
        assertEquals(3, again.probationStreak(day(14)))
    }

    @Test
    fun `an evening left unanswered is a gap`() {
        val before = kept(broken, 1..10)
        // Day 11 is skipped altogether.
        assertEquals(0, before.probationStreak(day(12)))

        val after = probationAnswered(before, kept = true, date = day(12), now = now)
        assertEquals(day(12), after.probationKeptSince)
        assertEquals(1, after.probationStreak(day(12)))
    }

    @Test
    fun `only a contract on probation has a run`() {
        val active = kept(broken, 1..REINSTATEMENT_STREAK).copy(state = ContractState.ACTIVE)
        assertEquals(0, active.probationStreak(day(REINSTATEMENT_STREAK)))
        assertNull(restartedContract(active, day(REINSTATEMENT_STREAK), now))
    }
}
