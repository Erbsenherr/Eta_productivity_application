package com.example.eta.data.repository

import com.example.eta.data.local.ItemDao
import com.example.eta.domain.model.Stage
import kotlin.time.Instant

/**
 * Retires the tasks a group swallowed through "Task zur Subtask reduzieren".
 *
 * **Retired rather than deleted**, although the note says "gelöscht": deleting
 * cascades through `planned_blocks`, so a ToDo that was ever completed would take
 * that day out of the Erfolgsliste with it. Retired, it leaves every active list,
 * which is what "gone" means here — and what happened stays true.
 *
 * An extension on the DAO rather than a method on one repository, because both
 * [ItemRepository] and [RecurringTaskService] write cards and either of them can be
 * the one that saves a group. Two copies of this would be two chances for one of
 * them to start deleting instead.
 */
suspend fun ItemDao.retireFolded(ids: Collection<String>, now: Instant) {
    if (ids.isEmpty()) return
    val rows = ids.mapNotNull { findById(it) }
        .map { it.copy(stage = Stage.DONE, completedAt = now, updatedAt = now) }
    if (rows.isNotEmpty()) upsertAll(rows)
}
