package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import java.util.UUID

/**
 * One rule of the Regelwerk (step 53): a question and its answer — "Was
 * passiert, wenn …?" and what the user decided happens then.
 *
 * Nothing in the app reads a rule. It is the user's own ruling, written down
 * once so it does not have to be decided again on the day the case comes up;
 * its own table, like the Notiz-Merger's notes, because it belongs to no list
 * and no plan. [position] is the order the tab shows them in, new ones last.
 */
@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val question: String,
    val answer: String,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
