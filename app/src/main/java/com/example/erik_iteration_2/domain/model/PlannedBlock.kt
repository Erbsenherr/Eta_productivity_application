package com.example.erik_iteration_2.domain.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Ignore
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.UUID

/**
 * Where a block came from. Drives how it is drawn in the day planner:
 * recurring and imported blocks have square corners and are not movable,
 * dragged-in ToDos have rounded corners.
 */
enum class BlockOrigin {
    RECURRING,
    DRAGGED,
    CALENDAR_IMPORT,
}

/**
 * One concrete occurrence of an [Item] on one day — the thing the user actually
 * checks off. A weekly item produces one block per week, each with its own
 * completion and its own yield.
 *
 * Times are wall-clock on purpose: planning means "07:00 to 08:00 on that day",
 * not a point on the absolute timeline.
 */
@Entity(
    tableName = "planned_blocks",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        /**
         * One occurrence per item per day, enforced by the database.
         *
         * Materialization reads what exists and then writes what is missing; two
         * runs overlapping meant both read "nothing there" and both inserted, and
         * the random-UUID primary key made those two perfectly legal rows. The
         * invariant has to live here, not in the timing of the callers.
         *
         * Covers the foreign key on `itemId` as its prefix, so no separate index.
         */
        Index(value = ["itemId", "date"], unique = true),
        Index("date"),
    ],
)
data class PlannedBlock(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val date: LocalDate,
    val start: LocalTime,
    /** What the plan allots. The user may correct this on completion via [actualDuration]. */
    val plannedDuration: Duration,
    val origin: BlockOrigin,
    val completedAt: Instant? = null,
    /** Recorded when checking off; falls back to [plannedDuration] for the yield. */
    val actualDuration: Duration? = null,
    /**
     * The journey there, which runs *before* [start], and the break that follows
     * the task.
     *
     * Two columns rather than two extra blocks. Each extra block would need an
     * [Item] to point at, and with it an extra definition, two more rows in every
     * list, two more questions in the evening reevaluation, and a moved task that
     * has to drag two other rows along atomically. As columns there is nothing to
     * keep in step: the block still starts when it says it starts, and the
     * **container** it occupies is [startMinute] − [travelBefore] to end +
     * [breakAfter]. That container is what every question about *time* is asked;
     * every question about *points* still gets [effectiveDuration], because
     * travelling and resting pay nothing.
     */
    val travelBefore: Duration? = null,
    val breakAfter: Duration? = null,
    /** Set when the evening reevaluation drops this occurrence instead of completing it. */
    val discardedAt: Instant? = null,
    /** The "Nachholen von …" ToDo this discarded occurrence spawned, if any. */
    val makeUpItemId: String? = null,
    /**
     * A note about *this* date only. Shown ahead of the item's own note, because
     * it is the one that says something about today.
     */
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    @get:Ignore
    val isCompleted: Boolean get() = completedAt != null

    @get:Ignore
    val isDiscarded: Boolean get() = discardedAt != null

    /** Still awaiting a decision in the evening reevaluation. */
    @get:Ignore
    val isOpen: Boolean get() = completedAt == null && discardedAt == null

    @get:Ignore
    val isMovable: Boolean get() = origin == BlockOrigin.DRAGGED

    /** Duration to bill: the corrected one if given, otherwise what was planned. */
    @get:Ignore
    val effectiveDuration: Duration get() = actualDuration ?: plannedDuration

    /** Whether this block brings a journey or a break with it. */
    @get:Ignore
    val hasMargins: Boolean get() = travelBefore != null || breakAfter != null
}
