package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * One night that differs from the standing answer — set by holding the shaded
 * night in the day planner, for that night and no other.
 *
 * Keyed by the date the night **ends** on, like everything about nights: the
 * night into Tuesday is Tuesday's, whichever side of midnight it begins on.
 * Only the two hours are stored. Winding down keeps the length the setup gives
 * it and moves with [sleep], so there is no third answer to keep in step.
 */
@Entity(tableName = "night_overrides")
data class NightOverride(
    @PrimaryKey val date: LocalDate,
    val sleep: LocalTime,
    val wake: LocalTime,
    val updatedAt: Instant,
)

/**
 * A warning of the planner the user has waved away for one day.
 *
 * Stored rather than held in the screen, for the reason the Überschneidung box
 * stores its own: a warning that comes back on every process death is one that
 * cannot be answered. [kind] is a `PlannerWarning` name.
 */
@Entity(tableName = "planner_dismissals", primaryKeys = ["date", "kind"])
data class PlannerDismissal(
    val date: LocalDate,
    val kind: String,
    val dismissedAt: Instant,
)
