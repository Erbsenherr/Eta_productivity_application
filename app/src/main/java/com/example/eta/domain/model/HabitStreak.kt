package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import java.util.UUID

/** What a streak counts (step 48). */
enum class StreakKind {
    /** A standing task, matched by its normalized name like a reward's binding. */
    TASK,

    /** Getting up **and** going to bed on time — one streak, both have to hold. */
    SLEEP,

    /**
     * The Tagesabschluss-Serie: the run of settled days the dashboard has always
     * shown, paid and charged like a streak while the feature is on. Exactly
     * one row of it, made by the first evening that needs it; it is never
     * ended and never asks to be restarted — it always runs.
     */
    SERIES,
}

/**
 * One streak on the Streaks tab (step 48).
 *
 * Named apart from `domain.streak.Streak`, the value the dashboard's run of
 * settled days is computed as.
 *
 * [length] is the number of consecutive completions; **zero is a streak begun
 * and waiting** for its first — "Schließe die Task ab, um den Streak zu
 * starten" — and a waiting streak cannot break. [lastCountedOn] is the last
 * evening that settled it, so one evening counts once however often the screen
 * is opened.
 */
@Entity(tableName = "streaks")
data class HabitStreak(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val kind: StreakKind,
    /** [StreakKind.TASK]: the normalized name of the standing task. */
    val taskName: String? = null,
    /** What the tab shows: the task's name as it was written when the streak began. */
    val label: String,
    val length: Int = 0,
    val lastCountedOn: LocalDate? = null,
    val startedAt: Instant,
    /** Broken and let go, or ended by hand. An ended streak is not shown. */
    val endedAt: Instant? = null,
    val updatedAt: Instant,
) {
    @get:Ignore
    val isRunning: Boolean get() = endedAt == null

    /** Begun, and still waiting for the completion that starts it. */
    @get:Ignore
    val isWaiting: Boolean get() = length == 0
}
