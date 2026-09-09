package com.example.erik_iteration_2.domain.model

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import java.util.UUID

/** Normalization used for Sperrliste lookups, so re-creation is blocked case- and space-insensitively. */
fun normalizeName(name: String): String = name.trim().lowercase()

/** Prefix of auto-created catch-up ToDos. */
const val MAKE_UP_NAME_PREFIX = "Nachholen von "

/**
 * The *definition* of a card: ToDo/Deadline/Wiederkehrend/Spend.
 *
 * An Item says what a thing is, not when it happens. Concrete placement on a day
 * lives in [PlannedBlock] — a RECURRING item has many blocks over time, each
 * completed and yielding points on its own.
 *
 * Which fields are populated depends on [type]; use the companion factories
 * rather than the raw constructor so each type only gets the fields it has.
 */
@Entity(
    tableName = "items",
    indices = [Index("normalizedName"), Index("stage"), Index("type")],
)
data class Item(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val type: ItemType,
    val name: String,
    /** Kept in sync with [name] — change both together via [renamed]. */
    val normalizedName: String = normalizeName(name),
    val stage: Stage,
    val category: Category? = null,
    /** What part this plays in the day, where a rule has to recognise it. */
    val role: ItemRole? = null,
    /**
     * A note that belongs to the task itself, and so follows every occurrence.
     * The note for a single date lives on [PlannedBlock] instead.
     */
    val note: String? = null,
    val priority: Priority? = null,
    /** TODO only: the date the user is aiming for. Unlocks a week earlier, see [availableFrom]. */
    val targetDate: LocalDate? = null,
    /** Planning estimate for TODO/RECURRING. The value actually earned comes from the block. */
    val estimatedDuration: Duration? = null,
    val deadlineAt: Instant? = null,
    val recurrenceRule: RecurrenceRule? = null,
    /** RECURRING only: the time of day its blocks are placed at. */
    val startTime: LocalTime? = null,
    /**
     * Defaults stamped onto every block this definition produces: the journey
     * there and the break afterwards. See [PlannedBlock.travelBefore].
     */
    val travelBefore: Duration? = null,
    val breakAfter: Duration? = null,
    /**
     * Whether `task_end.mp3` announces the end of this task's planned time.
     *
     * On the definition rather than the block, because it is a property of the
     * kind of thing this is — a task worth being told the end of stays one on
     * every occurrence. Off for everything that existed before the switch did:
     * the setup's frame of the day would otherwise start chiming all day, and
     * nobody agreed to that by upgrading.
     */
    val endSound: Boolean = false,
    /** SPEND only, signed: Custom Earn is positive (+1.5/h), Custom Spend negative (-5/h). */
    val pointsPerHour: Double? = null,
    /**
     * The planning cycle this was taken on as a weekly goal.
     *
     * What makes "the current week's goals" answerable after an item has left
     * Stage.WEEK — it leaves as soon as it is planned into a day, long before it
     * is done. Cleared when the item is handed back to the Sammelliste.
     */
    val weekStartedOn: LocalDate? = null,
    /** When this entered the Sammelliste — drives the one-month rule into the Sperrliste. */
    val enteredCollectionAt: Instant? = null,
    /** Stage.LOCKED only: blocked from re-creation until this instant. */
    val lockedUntil: Instant? = null,
    /** Retires a one-shot item (TODO/DEADLINE/SPEND). RECURRING items are never retired this way. */
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** ToDos become draggable in the weekly planner one week before their target date. */
    @get:Ignore
    val availableFrom: LocalDate? get() = targetDate?.minus(DatePeriod(days = 7))

    /**
     * Whether this may be pulled into a plan for [date]. A ToDo without a target
     * date — an auto-created make-up, for instance — is available right away.
     */
    fun isAvailableOn(date: LocalDate): Boolean =
        availableFrom?.let { date >= it } ?: true

    /**
     * Quick-Add cards are created bare from the dashboard and gain their
     * attributes in the evening. Only concretized cards can be planned.
     *
     * The question differs by type, because what "plannable" means does: a ToDo
     * needs the three the revolver sorts and bills by, a recurring definition
     * needs exactly what `expandRecurring` refuses to guess at — without a rule,
     * a start time and a duration it lays down no occurrence at all.
     */
    @get:Ignore
    val isConcretized: Boolean
        get() = when (type) {
            ItemType.TODO ->
                category != null && priority != null && estimatedDuration != null

            ItemType.RECURRING ->
                recurrenceRule != null && startTime != null && estimatedDuration != null

            else -> true
        }

    /**
     * The card with every answer a plannable ToDo owes, filled in.
     *
     * On the model rather than in a repository because it carries an invariant:
     * whatever it is handed, what comes back satisfies [isConcretized]. Three
     * screens now finish a card — the evening step, the Listen-Tab's editor and
     * the weekly planning — and each of them writing its own `copy` is three
     * places for one of the three fields to be forgotten.
     */
    fun concretized(
        category: Category,
        priority: Priority,
        targetDate: LocalDate,
        estimatedDuration: Duration,
        travelBefore: Duration? = this.travelBefore,
        breakAfter: Duration? = this.breakAfter,
        endSound: Boolean = this.endSound,
    ): Item = copy(
        category = category,
        priority = priority,
        targetDate = targetDate,
        estimatedDuration = estimatedDuration,
        travelBefore = travelBefore,
        breakAfter = breakAfter,
        endSound = endSound,
    )

    /** Renames while keeping [normalizedName] consistent. Prefer this over a plain copy. */
    fun renamed(newName: String, now: Instant) = copy(
        name = newName,
        normalizedName = normalizeName(newName),
        updatedAt = now,
    )

    companion object {
        /** Quick-Add from the dashboard: name only, attributes filled in during daily planning. */
        fun newQuickTodo(
            name: String,
            now: Instant,
        ) = Item(
            type = ItemType.TODO,
            name = name,
            stage = Stage.COLLECTION,
            enteredCollectionAt = now,
            createdAt = now,
            updatedAt = now,
        )

        /**
         * The recurring counterpart of [newQuickTodo]: a name and nothing else.
         *
         * It waits in the Sammelliste like any other note — same one-month clock,
         * same Sperrliste at the end of it — and the evening's concretizing step
         * asks it which days, when and how long. Until then it carries no rule,
         * so the expansion passes over it and it occupies no day.
         */
        fun newQuickRecurring(
            name: String,
            now: Instant,
        ) = Item(
            type = ItemType.RECURRING,
            name = name,
            stage = Stage.COLLECTION,
            enteredCollectionAt = now,
            createdAt = now,
            updatedAt = now,
        )

        fun newTodo(
            name: String,
            category: Category,
            priority: Priority,
            targetDate: LocalDate,
            estimatedDuration: Duration,
            now: Instant,
        ) = Item(
            type = ItemType.TODO,
            name = name,
            stage = Stage.COLLECTION,
            category = category,
            priority = priority,
            targetDate = targetDate,
            estimatedDuration = estimatedDuration,
            enteredCollectionAt = now,
            createdAt = now,
            updatedAt = now,
        )

        fun newDeadline(
            name: String,
            category: Category,
            deadlineAt: Instant,
            now: Instant,
        ) = Item(
            type = ItemType.DEADLINE,
            name = name,
            stage = Stage.DAY,
            category = category,
            deadlineAt = deadlineAt,
            createdAt = now,
            updatedAt = now,
        )

        /**
         * A recurring definition. [category] may be null: the framework blocks the
         * setup questionnaire lays down — sleep prep, lunch break, free time —
         * occupy the day so the free-hour maths works, without paying points for it.
         *
         * [id] is passed explicitly only by the questionnaire, which derives stable
         * ids from its answers so re-running it updates rather than duplicates.
         */
        fun newRecurring(
            name: String,
            category: Category?,
            recurrenceRule: RecurrenceRule,
            startTime: LocalTime,
            estimatedDuration: Duration,
            now: Instant,
            id: String = UUID.randomUUID().toString(),
            role: ItemRole? = null,
        ) = Item(
            id = id,
            type = ItemType.RECURRING,
            name = name,
            stage = Stage.DAY,
            category = category,
            role = role,
            recurrenceRule = recurrenceRule,
            startTime = startTime,
            estimatedDuration = estimatedDuration,
            createdAt = now,
            updatedAt = now,
        )

        /**
         * The ToDo offered when a recurring occurrence was dropped and the user
         * wants to catch up. Inherits category and duration from what was missed,
         * carries no target date so it can be planned immediately, and is priority
         * "Muss geschehen".
         */
        fun newMakeUpTodo(
            missed: Item,
            now: Instant,
        ) = Item(
            type = ItemType.TODO,
            name = MAKE_UP_NAME_PREFIX + missed.name,
            stage = Stage.COLLECTION,
            category = missed.category,
            priority = Priority.MUST,
            estimatedDuration = missed.estimatedDuration,
            // It is the same task on another day, so it keeps the journey it
            // needs, the break it earns and whether its end announces itself.
            travelBefore = missed.travelBefore,
            breakAfter = missed.breakAfter,
            endSound = missed.endSound,
            enteredCollectionAt = now,
            createdAt = now,
            updatedAt = now,
        )

        /** Covers Custom Spend, Custom Earn and Social from the second revolver. */
        fun newSpend(
            name: String,
            pointsPerHour: Double,
            now: Instant,
        ) = Item(
            type = ItemType.SPEND,
            name = name,
            stage = Stage.DAY,
            pointsPerHour = pointsPerHour,
            createdAt = now,
            updatedAt = now,
        )
    }
}
