package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction

/**
 * Wipes the database back to its state before first launch — a debug affordance,
 * not something the app does on its own.
 *
 * Every table is listed by hand rather than swept in a loop: a table added later
 * has to be added here consciously, and forgetting one is then a visible omission
 * instead of silently surviving a "reset".
 */
@Dao
abstract class ResetDao {

    /** Blocks first, then items: the cascade would do it, but the order documents intent. */
    @Transaction
    open suspend fun clearEverything() {
        clearSubtaskChecks()
        clearSubtasks()
        clearPlannedBlocks()
        clearItems()
        clearDayPlans()
        clearPointsTransactions()
        clearContracts()
        clearJournal()
        clearVacationRules()
        clearVacations()
        clearCalendarEvents()
        clearCalendarSources()
        clearReminders()
        clearConflictDismissals()
        clearSetup()
    }

    @Query("DELETE FROM subtask_checks")
    abstract suspend fun clearSubtaskChecks()

    @Query("DELETE FROM subtasks")
    abstract suspend fun clearSubtasks()

    @Query("DELETE FROM planned_blocks")
    abstract suspend fun clearPlannedBlocks()

    @Query("DELETE FROM items")
    abstract suspend fun clearItems()

    @Query("DELETE FROM day_plans")
    abstract suspend fun clearDayPlans()

    @Query("DELETE FROM points_transactions")
    abstract suspend fun clearPointsTransactions()

    @Query("DELETE FROM contracts")
    abstract suspend fun clearContracts()

    @Query("DELETE FROM journal_entries")
    abstract suspend fun clearJournal()

    @Query("DELETE FROM vacation_rules")
    abstract suspend fun clearVacationRules()

    @Query("DELETE FROM vacations")
    abstract suspend fun clearVacations()

    @Query("DELETE FROM calendar_events")
    abstract suspend fun clearCalendarEvents()

    @Query("DELETE FROM calendar_sources")
    abstract suspend fun clearCalendarSources()

    @Query("DELETE FROM reminders")
    abstract suspend fun clearReminders()

    @Query("DELETE FROM conflict_dismissals")
    abstract suspend fun clearConflictDismissals()

    @Query("DELETE FROM user_setup")
    abstract suspend fun clearSetup()
}
