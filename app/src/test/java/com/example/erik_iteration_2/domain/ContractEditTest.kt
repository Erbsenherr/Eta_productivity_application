package com.example.erik_iteration_2.domain

import com.example.erik_iteration_2.domain.contract.editedContract
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.ContractEffort
import com.example.erik_iteration_2.domain.model.ContractState
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one wording change a contract is allowed, and what it costs. */
class ContractEditTest {

    private val now = Instant.parse("2026-09-15T18:00:00Z")
    private val today = LocalDate(2026, 9, 15)

    private fun contract(
        state: ContractState = ContractState.ACTIVE,
        editedAt: Instant? = null,
    ) = Contract(
        id = "c",
        slot = 0,
        title = "Sport",
        conditions = "Dreimal die Woche laufen.",
        breachDefinition = "Zwei Wochen ohne Lauf.",
        effort = ContractEffort.MITTEL,
        signature = "Erik",
        // Signed a fortnight ago, running for four weeks.
        signedOn = LocalDate(2026, 9, 1),
        endsOn = LocalDate(2026, 9, 29),
        state = state,
        editedAt = editedAt,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `the term restarts and keeps its length`() {
        val edited = editedContract(contract(), "Sport", "Viermal laufen.", "Eine Woche ohne", today, now)

        assertNotNull(edited)
        // 28 days of term, now measured from today rather than from 1 September.
        assertEquals(today, edited!!.signedOn)
        assertEquals(LocalDate(2026, 10, 13), edited.endsOn)
    }

    @Test
    fun `the run-up towards legacy is spent`() {
        val edited = editedContract(contract(), "Sport", "Viermal laufen.", "x", today, now)!!

        // A fortnight in before, back to nothing after: the month starts again.
        assertTrue(contract().canUpgradeToLegacy(LocalDate(2026, 10, 2)))
        assertFalse(edited.canUpgradeToLegacy(LocalDate(2026, 10, 2)))
        assertTrue(edited.canUpgradeToLegacy(LocalDate(2026, 10, 16)))
    }

    @Test
    fun `the effort and the payout do not move`() {
        val edited = editedContract(contract(), "Sport", "Viermal laufen.", "x", today, now)!!

        assertEquals(ContractEffort.MITTEL, edited.effort)
        assertEquals(contract().dailyPayout, edited.dailyPayout, 0.0)
    }

    @Test
    fun `only the text changes`() {
        val edited = editedContract(contract(), "Laufen", "Viermal laufen.", "Eine Woche ohne", today, now)!!

        assertEquals("Laufen", edited.title)
        assertEquals("Viermal laufen.", edited.conditions)
        assertEquals("Eine Woche ohne", edited.breachDefinition)
        assertEquals(0, edited.slot)
    }

    @Test
    fun `a second change is refused`() {
        val once = editedContract(contract(), "a", "b", "c", today, now)!!

        assertFalse(once.isEditable)
        assertNull(editedContract(once, "d", "e", "f", today, now))
    }

    @Test
    fun `legacy broken and fulfilled contracts cannot be changed`() {
        listOf(ContractState.LEGACY, ContractState.BROKEN, ContractState.FULFILLED).forEach { state ->
            assertNull(editedContract(contract(state = state), "a", "b", "c", today, now))
        }
    }

    @Test
    fun `an empty title falls back to the conditions, as signing does`() {
        val edited = editedContract(contract(), "", "Viermal die Woche laufen gehen.", "x", today, now)!!

        assertEquals("Viermal die Woche laufen gehen.", edited.title)
    }
}
