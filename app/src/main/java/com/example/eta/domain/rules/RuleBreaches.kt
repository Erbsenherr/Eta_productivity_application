package com.example.eta.domain.rules

import com.example.eta.domain.model.Rule
import com.example.eta.domain.model.RuleBreach
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** How far back the morning looks for rules to keep in mind (step 57). */
const val WATCH_DAYS = 14

/** A breach on a day, with the rule it broke. */
data class ActiveConsequence(val rule: Rule, val breach: RuleBreach)

/** The breaches reported for [date] whose rule still exists, earliest first. */
fun consequencesOn(date: LocalDate, breaches: List<RuleBreach>, rules: List<Rule>): List<ActiveConsequence> {
    val byId = rules.associateBy { it.id }
    return breaches
        .filter { it.date == date }
        .sortedBy { it.createdAt }
        .mapNotNull { breach -> byId[breach.ruleId]?.let { ActiveConsequence(it, breach) } }
}

/**
 * The days whose free time is under a consequence — a breach of a rule whose
 * consequence is tied to the free time. What the hatching asks.
 */
fun freeTimeConsequenceDays(breaches: List<RuleBreach>, rules: List<Rule>): Set<LocalDate> {
    val bound = rules.filter { it.consequenceOnFreeTime }.map { it.id }.toSet()
    return breaches.filter { it.ruleId in bound }.map { it.date }.toSet()
}

/**
 * The rules broken in the [WATCH_DAYS] before [today] — today itself not, its
 * breach being on the dashboard in red already — most recently broken first,
 * each once. What the morning reminds of.
 */
fun rulesToWatch(today: LocalDate, breaches: List<RuleBreach>, rules: List<Rule>): List<Rule> {
    val from = today.minus(DatePeriod(days = WATCH_DAYS))
    val byId = rules.associateBy { it.id }
    return breaches
        .filter { it.date >= from && it.date < today }
        .sortedByDescending { it.date }
        .mapNotNull { byId[it.ruleId] }
        .distinctBy { it.id }
}

/** The line a bound consequence writes into the free time's note. */
fun consequenceLine(consequence: String): String = "⚠ Konsequenz: ${consequence.trim()}"

/** [note] with the consequence's line under it — once. */
fun withConsequenceNote(note: String?, consequence: String): String {
    val line = consequenceLine(consequence)
    val lines = note.orEmpty().lines().filter { it.isNotBlank() }
    if (line in lines) return lines.joinToString("\n")
    return (lines + line).joinToString("\n")
}

/** [note] without that line; null when nothing else was in it. */
fun withoutConsequenceNote(note: String?, consequence: String): String? {
    val line = consequenceLine(consequence)
    return note.orEmpty().lines()
        .filter { it.isNotBlank() && it != line }
        .joinToString("\n")
        .ifBlank { null }
}
