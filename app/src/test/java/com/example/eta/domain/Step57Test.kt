package com.example.eta.domain

import com.example.eta.domain.model.Rule
import com.example.eta.domain.model.RuleBreach
import com.example.eta.domain.rules.consequenceLine
import com.example.eta.domain.rules.consequencesOn
import com.example.eta.domain.rules.freeTimeConsequenceDays
import com.example.eta.domain.rules.rulesToWatch
import com.example.eta.domain.rules.withConsequenceNote
import com.example.eta.domain.rules.withoutConsequenceNote
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Step 57: a rule broken, its consequence, and the morning's reminder. */
class Step57Test {

    private val now = Instant.parse("2026-11-11T08:00:00Z")
    private val today = LocalDate(2026, 11, 11)

    private fun rule(id: String, onFreeTime: Boolean = false, consequence: String? = "keine Serien") = Rule(
        id = id,
        question = "Was, wenn $id?",
        answer = "",
        consequence = consequence,
        consequenceOnFreeTime = onFreeTime,
        position = 0,
        createdAt = now,
        updatedAt = now,
    )

    private fun breach(ruleId: String, date: LocalDate, minute: Int = 0) =
        RuleBreach(ruleId = ruleId, date = date, createdAt = now + minute.minutes, updatedAt = now)

    @Test
    fun `today's consequences are today's breaches of rules that still exist`() {
        val rules = listOf(rule("a"), rule("b"))
        val active = consequencesOn(
            today,
            listOf(
                breach("b", today, minute = 5),
                breach("a", today, minute = 1),
                breach("a", LocalDate(2026, 11, 10)),
                breach("gone", today),
            ),
            rules,
        )
        assertEquals(listOf("a", "b"), active.map { it.rule.id })
    }

    @Test
    fun `only a rule tied to the free time hatches its day`() {
        val rules = listOf(rule("bound", onFreeTime = true), rule("loose"))
        val days = freeTimeConsequenceDays(
            listOf(breach("bound", today), breach("loose", LocalDate(2026, 11, 9))),
            rules,
        )
        assertEquals(setOf(today), days)
    }

    @Test
    fun `the morning reminds of the last two weeks, not of today or longer ago`() {
        val rules = listOf(rule("recent"), rule("old"), rule("today"), rule("twice"))
        val watch = rulesToWatch(
            today,
            listOf(
                breach("recent", LocalDate(2026, 11, 10)),
                breach("twice", LocalDate(2026, 11, 3)),
                breach("twice", LocalDate(2026, 10, 30)),
                breach("old", LocalDate(2026, 10, 27)),
                breach("today", today),
            ),
            rules,
        )
        assertEquals(listOf("recent", "twice"), watch.map { it.id })
    }

    @Test
    fun `the consequence is written into the note once, and taken out again`() {
        val once = withConsequenceNote("Spazieren gehen", "keine Serien")
        assertEquals("Spazieren gehen\n" + consequenceLine("keine Serien"), once)
        assertEquals(once, withConsequenceNote(once, "keine Serien"))

        assertEquals("Spazieren gehen", withoutConsequenceNote(once, "keine Serien"))
        assertNull(withoutConsequenceNote(withConsequenceNote(null, "keine Serien"), "keine Serien"))
        assertTrue(withConsequenceNote(null, "x").startsWith("⚠"))
    }
}
