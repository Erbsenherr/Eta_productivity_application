package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import java.util.UUID

/**
 * A reminder the user set by hand: a moment and what to say then.
 *
 * Nothing to do with the plan — no item, no block, no points. It is the one thing
 * in the app that is purely "tell me this at that time", which is also why it
 * gets a tab of its own rather than a corner of the day planner.
 *
 * The moment is an [Instant] rather than a wall-clock date and time: unlike a
 * block, which is "07:00 on that day" by design, a reminder was set for a
 * particular moment and should ring then.
 */
@Entity(
    tableName = "reminders",
    indices = [Index("at")],
)
data class Reminder(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val at: Instant,
    /**
     * When it actually rang. Non-null takes it out of the "Anstehend" list, and is
     * what keeps a reminder from ringing twice when the alarm is re-aimed.
     */
    val firedAt: Instant? = null,
    /**
     * The task this reminder was derived from, where it was not set by hand.
     *
     * The "Erinnerung" extra of a ToDo or a standing task produces an ordinary
     * row in this table — a reminder the user cannot find in the Erinnerungen tab
     * is one they cannot change — and these two columns are what lets the app
     * keep that row in step with the plan: aimed at the task's next occurrence,
     * moved when the block moves, and gone when the task loses the extra.
     *
     * Plain columns rather than a foreign key, deliberately. `planned_blocks`
     * cascades on delete, and a reminder that already rang is a record of
     * something that happened; it must not disappear because the schedule was
     * laid down again around it.
     */
    val itemId: String? = null,
    val blockId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    @get:Ignore
    val isPending: Boolean get() = firedAt == null

    /** Derived from a task rather than typed into the tab. */
    @get:Ignore
    val isAutomatic: Boolean get() = itemId != null
}
