package com.example.erik_iteration_2.data.local

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.ItemType
import com.example.erik_iteration_2.domain.model.Stage
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface ItemDao {

    @Upsert
    suspend fun upsert(item: Item)

    @Upsert
    suspend fun upsertAll(items: List<Item>)

    @Delete
    suspend fun delete(item: Item)

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun findById(id: String): Item?

    /** Backs each of the six list views; ordering is refined per screen. */
    @Query("SELECT * FROM items WHERE stage = :stage ORDER BY createdAt")
    fun observeByStage(stage: Stage): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE stage = :stage AND type = :type ORDER BY createdAt")
    fun observeByStageAndType(stage: Stage, type: ItemType): Flow<List<Item>>

    /**
     * Sperrliste check before creating a ToDo: an active lock on the same
     * normalized name blocks re-creation until it expires.
     */
    @Query(
        """
        SELECT * FROM items
        WHERE stage = 'LOCKED'
          AND normalizedName = :normalizedName
          AND lockedUntil > :now
        LIMIT 1
        """,
    )
    suspend fun findActiveLock(normalizedName: String, now: Instant): Item?

    /**
     * ToDos that have sat in the Sammelliste since before [threshold] and are due
     * to be pushed into the Sperrliste.
     */
    @Query(
        """
        SELECT * FROM items
        WHERE stage = 'COLLECTION'
          AND enteredCollectionAt IS NOT NULL
          AND enteredCollectionAt <= :threshold
        """,
    )
    suspend fun findStaleCollectionItems(threshold: Instant): List<Item>

    /** Dashboard box 3: open deadlines, soonest first. */
    @Query(
        """
        SELECT * FROM items
        WHERE type = 'DEADLINE'
          AND completedAt IS NULL
          AND deadlineAt IS NOT NULL
        ORDER BY deadlineAt
        """,
    )
    fun observeOpenDeadlines(): Flow<List<Item>>

    /**
     * Sammelliste entries old enough to be within the Sperrliste warning window —
     * dashboard box 0.
     */
    @Query(
        """
        SELECT * FROM items
        WHERE stage = 'COLLECTION'
          AND enteredCollectionAt IS NOT NULL
          AND enteredCollectionAt <= :threshold
        ORDER BY enteredCollectionAt
        """,
    )
    fun observeCollectionEnteredBefore(threshold: Instant): Flow<List<Item>>

    /**
     * Weekly goals taken on in an earlier cycle that are still in flight.
     *
     * Still in flight means the week list or a day — an item leaves Stage.WEEK
     * when it is planned, not when it is done, so the stamp is what carries the
     * commitment through. Anything handed back to the Sammelliste has had its
     * stamp cleared and no longer counts.
     */
    @Query(
        """
        SELECT * FROM items
        WHERE weekStartedOn IS NOT NULL
          AND weekStartedOn < :cycleStart
          AND stage IN ('WEEK', 'DAY')
        ORDER BY weekStartedOn
        """,
    )
    fun observeUnfinishedWeekGoals(cycleStart: LocalDate): Flow<List<Item>>

    /** One-shot ToDos whose occurrence on [date] was completed — ready to retire. */
    @Query(
        """
        SELECT i.* FROM items AS i
        JOIN planned_blocks AS b ON b.itemId = i.id
        WHERE b.date = :date
          AND b.completedAt IS NOT NULL
          AND i.type = 'TODO'
          AND i.stage != 'DONE'
        """,
    )
    suspend fun findCompletedTodosOn(date: LocalDate): List<Item>

    /** Every recurring definition, used to expand blocks and to review them nightly. */
    @Query("SELECT * FROM items WHERE type = 'RECURRING' AND completedAt IS NULL")
    fun observeRecurringDefinitions(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE type = 'RECURRING' AND completedAt IS NULL")
    suspend fun findRecurringDefinitions(): List<Item>

    /**
     * The definitions the setup questionnaire owns, found by their id prefix.
     * Answering it again has to update or retire exactly these.
     */
    @Query("SELECT * FROM items WHERE id LIKE :prefix || '%'")
    suspend fun findByIdPrefix(prefix: String): List<Item>
}
