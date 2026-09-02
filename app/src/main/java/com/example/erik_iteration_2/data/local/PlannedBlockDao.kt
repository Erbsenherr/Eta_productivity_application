package com.example.erik_iteration_2.data.local

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.PlannedBlock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * A block together with the item defining it — what the day planner draws.
 *
 * Resolved via [Relation] rather than a JOIN because both tables carry `id`,
 * `createdAt` and `updatedAt`, which a `SELECT *` join could not disambiguate.
 */
data class BlockWithItem(
    @Embedded val block: PlannedBlock,
    @Relation(parentColumns = ["itemId"], entityColumns = ["id"])
    val item: Item,
)

/** Identity of an already materialized occurrence. */
data class BlockSlot(
    val itemId: String,
    val date: LocalDate,
)

@Dao
interface PlannedBlockDao {

    @Upsert
    suspend fun upsert(block: PlannedBlock)

    @Upsert
    suspend fun upsertAll(blocks: List<PlannedBlock>)

    /**
     * Inserts only the occurrences that are not there yet.
     *
     * @Upsert cannot be used for materialization: it matches on the primary key,
     * which is a fresh UUID every time, so it would happily write a second row for
     * a day that already has one. The unique index on (itemId, date) turns that
     * into a conflict, and IGNORE turns the conflict into a no-op — which is what
     * "materialize what is missing" means.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissing(blocks: List<PlannedBlock>): List<Long>

    @Delete
    suspend fun delete(block: PlannedBlock)

    @Query("SELECT * FROM planned_blocks WHERE id = :id")
    suspend fun findById(id: String): PlannedBlock?

    @Query("SELECT * FROM planned_blocks WHERE date = :date ORDER BY start")
    fun observeForDate(date: LocalDate): Flow<List<PlannedBlock>>

    @Transaction
    @Query("SELECT * FROM planned_blocks WHERE date = :date ORDER BY start")
    fun observeForDateWithItems(date: LocalDate): Flow<List<BlockWithItem>>

    @Transaction
    @Query(
        """
        SELECT * FROM planned_blocks
        WHERE date BETWEEN :from AND :to
        ORDER BY date, start
        """,
    )
    fun observeForRangeWithItems(from: LocalDate, to: LocalDate): Flow<List<BlockWithItem>>

    /**
     * Blocks of a day still awaiting a decision — what the evening reevaluation
     * presents. Already discarded ones are excluded so it never asks twice.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM planned_blocks
        WHERE date = :date AND completedAt IS NULL AND discardedAt IS NULL
        ORDER BY start
        """,
    )
    suspend fun findOpenForDateWithItems(date: LocalDate): List<BlockWithItem>

    /** A stretch of days, once — what a holiday has to bring into line. */
    @Transaction
    @Query(
        """
        SELECT * FROM planned_blocks
        WHERE date BETWEEN :from AND :to
        ORDER BY date, start
        """,
    )
    suspend fun findForRangeWithItems(from: LocalDate, to: LocalDate): List<BlockWithItem>

    /** The whole day, once — what the evening reevaluation settles over. */
    @Transaction
    @Query("SELECT * FROM planned_blocks WHERE date = :date ORDER BY start")
    suspend fun findForDateWithItems(date: LocalDate): List<BlockWithItem>

    /** Completed blocks of a day — the input for the evening reward calculation. */
    @Transaction
    @Query("SELECT * FROM planned_blocks WHERE date = :date AND completedAt IS NOT NULL")
    suspend fun findCompletedForDateWithItems(date: LocalDate): List<BlockWithItem>

    @Query("DELETE FROM planned_blocks WHERE date = :date AND origin = 'DRAGGED'")
    suspend fun clearDraggedBlocksForDate(date: LocalDate)

    /**
     * The Erfolgsliste: everything ever checked off, newest first.
     *
     * Built from blocks rather than from items in Stage.DONE, because a recurring
     * item is never retired — only its individual occurrences complete.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM planned_blocks
        WHERE completedAt IS NOT NULL
        ORDER BY completedAt DESC
        """,
    )
    fun observeCompletedWithItems(): Flow<List<BlockWithItem>>

    /** The days the app actually planned — candidates for a catch-up. */
    @Query("SELECT DISTINCT date FROM planned_blocks WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun findDatesWithBlocks(from: LocalDate, to: LocalDate): List<LocalDate>

    /**
     * Clears out what a retired definition still had standing ahead of it.
     *
     * Only untouched occurrences from [from] on: a block already checked off or
     * dismissed is a record of what happened, and moving Sport from Monday to
     * Tuesday must not rewrite last Monday.
     */
    @Query(
        """
        DELETE FROM planned_blocks
        WHERE itemId IN (:itemIds)
          AND date >= :from
          AND completedAt IS NULL
          AND discardedAt IS NULL
        """,
    )
    suspend fun clearUpcomingFor(itemIds: List<String>, from: LocalDate): Int

    /** Which item/date pairs already have a block — guards recurrence expansion against duplicates. */
    @Query("SELECT itemId, date FROM planned_blocks WHERE date BETWEEN :from AND :to")
    suspend fun findExistingSlots(from: LocalDate, to: LocalDate): List<BlockSlot>
}
