package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import java.util.UUID

/**
 * One step inside a task — a name, optionally a note, and nothing else.
 *
 * Deliberately **not** an [Item]. A subtask has no duration, no category, no
 * priority, no deadline, no role and no block of its own: it earns nothing, is
 * never planned, never reaches the Sperrliste and never appears in any of the six
 * lists. Making it an Item with a parent id would have meant teaching every list
 * query, the expansion, the Erfolgsliste and the settlement to skip children —
 * six places for one of them to be forgotten.
 *
 * With subtasks in a table of their own, "every task is a group with an empty
 * subtask list" is literally true: nothing about an item changes by gaining one.
 *
 * The list belongs to the **definition**, like the name and the category, so
 * editing it changes every occurrence still to come. What belongs to one day is
 * the ticking, and that is [SubtaskCheck].
 */
@Entity(
    tableName = "subtasks",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    /** `itemId` first, so this covers the foreign key as its prefix. */
    indices = [Index(value = ["itemId", "position"])],
)
data class Subtask(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    /**
     * Where this sits in the group. Not unique per item on purpose: saving a
     * reordered list writes several rows, and a unique index would make the order
     * of those writes matter.
     */
    val position: Int,
    val name: String,
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * That one subtask was ticked off on one day.
 *
 * The row's *existence* is the tick — unticking deletes it, which is what makes
 * "what is still open" a difference of two sets rather than a state to keep in
 * step. Keyed by the block, not by the item, because a weekly group that is
 * worked through every Tuesday has to start each Tuesday empty.
 *
 * Both foreign keys cascade: a block cleared by an edit to its standing task
 * takes its ticks with it, and so does a subtask the user deletes.
 */
@Entity(
    tableName = "subtask_checks",
    primaryKeys = ["blockId", "subtaskId"],
    foreignKeys = [
        ForeignKey(
            entity = PlannedBlock::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Subtask::class,
            parentColumns = ["id"],
            childColumns = ["subtaskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    /** `blockId` is the primary key's prefix; the other key needs its own. */
    indices = [Index("subtaskId")],
)
data class SubtaskCheck(
    val blockId: String,
    val subtaskId: String,
    val checkedAt: Instant,
)
