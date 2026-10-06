package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.Reminder
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Upsert
    suspend fun upsert(reminder: Reminder)

    @Upsert
    suspend fun upsertAll(reminders: List<Reminder>)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun findById(id: String): Reminder?

    /** What has yet to ring, soonest first — the tab's list. */
    @Query("SELECT * FROM reminders WHERE firedAt IS NULL ORDER BY at")
    fun observePending(): Flow<List<Reminder>>

    /** The same, read once, for the alarm. */
    @Query("SELECT * FROM reminders WHERE firedAt IS NULL ORDER BY at")
    suspend fun findPending(): List<Reminder>

    /**
     * The reminders the app maintains itself, still waiting to ring.
     *
     * What the task-reminder sync reconciles against: anything here that the plan
     * no longer asks for is taken away, and what it does ask for is written. A
     * reminder that already rang is left alone — it is a record of something that
     * happened.
     */
    @Query("SELECT * FROM reminders WHERE firedAt IS NULL AND itemId IS NOT NULL")
    suspend fun findPendingAutomatic(): List<Reminder>

    /** Everything due by [until] that has not rung yet — including what a switched-off phone missed. */
    @Query("SELECT * FROM reminders WHERE firedAt IS NULL AND at <= :until ORDER BY at")
    suspend fun findDue(until: Instant): List<Reminder>
}
