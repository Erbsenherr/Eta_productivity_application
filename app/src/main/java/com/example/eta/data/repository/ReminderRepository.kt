package com.example.eta.data.repository

import com.example.eta.data.local.ReminderDao
import com.example.eta.domain.model.Reminder
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow

class ReminderRepository(
    private val dao: ReminderDao,
    private val clock: Clock = Clock.System,
) {

    fun observePending(): Flow<List<Reminder>> = dao.observePending()

    suspend fun findPending(): List<Reminder> = dao.findPending()

    suspend fun add(text: String, at: Instant) {
        val now = clock.now()
        dao.upsert(Reminder(text = text, at = at, createdAt = now, updatedAt = now))
    }

    /**
     * Changes what a reminder says and when.
     *
     * Read back by id rather than trusting the screen's copy, and `firedAt` is
     * cleared: moving a reminder is setting it again, so one that already rang can
     * be put back in the queue for a new time.
     */
    suspend fun update(id: String, text: String, at: Instant) {
        val existing = dao.findById(id) ?: return
        dao.upsert(existing.copy(text = text, at = at, firedAt = null, updatedAt = clock.now()))
    }

    suspend fun delete(id: String) = dao.delete(id)

    /**
     * Everything that is due by [now], marked as rung in the same breath.
     *
     * A second of slack for an alarm that arrives a hair early, which the system
     * is allowed to do.
     */
    suspend fun markDueAsFired(now: Instant): List<Reminder> {
        val due = dao.findDue(now + 1.seconds)
        if (due.isNotEmpty()) {
            dao.upsertAll(due.map { it.copy(firedAt = now, updatedAt = now) })
        }
        return due
    }
}
