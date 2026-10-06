package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.Item
import com.example.eta.domain.model.Stage
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

    /**
     * ToDos that can be folded into a group as steps: the week's goals and the
     * undated backlog.
     *
     * Nothing that already stands on a day — those are the planner's, and the way
     * to fold one of them in is to drag it onto the group. Nothing retired either.
     */
    @Query(
        """
        SELECT * FROM items
        WHERE type = 'TODO' AND completedAt IS NULL
          AND stage IN ('WEEK', 'COLLECTION')
        ORDER BY stage DESC, createdAt
        """,
    )
    fun observeFoldCandidates(): Flow<List<Item>>

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

    /**
     * Every card with a deadline still to be met, soonest first.
     *
     * Not `type = 'DEADLINE'` any more. A deadline turned out not to be a kind of
     * card but something a card *has* — it is asked for in the Extras box of an
     * ordinary ToDo — and nothing in the app ever created a DEADLINE item, which
     * is why the option could not be found. The old rows, if any exist, still
     * qualify: they carry a `deadlineAt` like everything else here.
     *
     * A retired card (`DONE`) and a banned one (`LOCKED`) are left out: neither
     * is something the user can still act on today.
     */
    @Query(
        """
        SELECT * FROM items
        WHERE deadlineAt IS NOT NULL
          AND completedAt IS NULL
          AND stage NOT IN ('DONE', 'LOCKED')
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
     * Goals of an earlier cycle that are still lying in the week list, unplanned.
     *
     * **Stage.WEEK only.** The rule is "work off what you took on before taking on
     * more", and what is still to be worked off is what is still lying there: a
     * goal that has been planned into a day has been dealt with as far as this
     * question goes, and the day it sits on is what holds it now. Counting
     * Stage.DAY as well meant a week could be planned out completely — revolver
     * empty, nothing left to place — and still refuse anything new, which is the
     * opposite of the intent. Anything handed back to the Sammelliste has had its
     * stamp cleared and never counted.
     */
    @Query(
        """
        SELECT * FROM items
        WHERE weekStartedOn IS NOT NULL
          AND weekStartedOn < :cycleStart
          AND stage = 'WEEK'
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
