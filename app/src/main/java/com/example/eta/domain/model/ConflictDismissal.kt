package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * A collision the user has told the app to stop mentioning.
 *
 * Stored rather than held in the screen, because a warning that came back every
 * time the process was killed would be a warning that cannot be answered.
 *
 * The id is the two block ids, sorted — see `conflictKey`. That is also what
 * limits a dismissal to the one day the user meant: one item has one block per
 * day, so the same two tasks colliding tomorrow are a different pair of ids and
 * ask again. [date] is carried only so old rows can be swept.
 */
@Entity(
    tableName = "conflict_dismissals",
    indices = [Index("date")],
)
data class ConflictDismissal(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val createdAt: Instant,
)
