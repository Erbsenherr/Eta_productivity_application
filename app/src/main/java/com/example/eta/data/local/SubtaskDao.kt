package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.Subtask
import com.example.eta.domain.model.SubtaskCheck
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface SubtaskDao {

    @Upsert
    suspend fun upsert(subtasks: List<Subtask>)

    @Delete
    suspend fun delete(subtasks: List<Subtask>)

    @Query("SELECT * FROM subtasks WHERE itemId = :itemId ORDER BY position")
    suspend fun forItem(itemId: String): List<Subtask>

    @Query("SELECT * FROM subtasks WHERE itemId = :itemId ORDER BY position")
    fun observeForItem(itemId: String): Flow<List<Subtask>>

    /**
     * Every subtask there is, which is what the screens showing many cards at once
     * need — the planner, the dashboard, the lists.
     *
     * Deliberately not narrowed to a set of item ids: the table holds a handful of
     * rows per task that has any at all, and a query per visible block would be
     * the expensive shape here.
     */
    @Query("SELECT * FROM subtasks ORDER BY itemId, position")
    fun observeAll(): Flow<List<Subtask>>

    @Query("SELECT * FROM subtasks ORDER BY itemId, position")
    suspend fun all(): List<Subtask>

    @Upsert
    suspend fun check(check: SubtaskCheck)

    @Query("DELETE FROM subtask_checks WHERE blockId = :blockId AND subtaskId = :subtaskId")
    suspend fun uncheck(blockId: String, subtaskId: String)

    /** The ticks of one day, for the planner and the "Gerade" box. */
    @Query(
        """
        SELECT * FROM subtask_checks
        WHERE blockId IN (SELECT id FROM planned_blocks WHERE date = :date)
        """,
    )
    fun observeChecksForDate(date: LocalDate): Flow<List<SubtaskCheck>>

    @Query(
        """
        SELECT * FROM subtask_checks
        WHERE blockId IN (SELECT id FROM planned_blocks WHERE date = :date)
        """,
    )
    suspend fun checksForDate(date: LocalDate): List<SubtaskCheck>
}
