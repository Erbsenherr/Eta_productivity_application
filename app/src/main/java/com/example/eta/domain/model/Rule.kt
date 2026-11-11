package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import java.util.UUID

/**
 * One rule of the Regelwerk (step 53): a question and its answer — "Was
 * passiert, wenn …?" and what the user decided happens then.
 *
 * Since step 57 a rule can be reported broken ([RuleBreach]); nothing else
 * in the app reads one. It is the user's own ruling, written down
 * once so it does not have to be decided again on the day the case comes up;
 * its own table, like the Notiz-Merger's notes, because it belongs to no list
 * and no plan. [position] is the order the tab shows them in, new ones last.
 */
@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val question: String,
    val answer: String,
    /** Step 57: "Ausnahmesituation" — when the rule does not apply. Optional. */
    val exception: String? = null,
    /**
     * Step 57: what follows a breach, in the user's words ("heute keine
     * Bildschirmzeit"). Shown in red on the dashboard on the day it is reported.
     */
    val consequence: String? = null,
    /**
     * The consequence is tied to the day's free time: the Freizeit block is
     * hatched red everywhere it is drawn, and carries the consequence as a note.
     */
    val consequenceOnFreeTime: Boolean = false,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * A rule reported broken (step 57), on [date] — the day its consequence is
 * active. [ruleId] is a plain column: deleting a rule deletes its breaches by
 * hand, in `RuleRepository.delete`.
 */
@Entity(tableName = "rule_breaches")
data class RuleBreach(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val ruleId: String,
    val date: kotlinx.datetime.LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
)
