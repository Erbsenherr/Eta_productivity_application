package com.example.eta.data.repository

import com.example.eta.data.local.ConflictDao
import com.example.eta.domain.model.ConflictDismissal
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Which collisions the dashboard has been told to keep quiet about.
 *
 * Keyed by the two block ids, which is what makes a dismissal last exactly the
 * one day the user meant: one item has one block per day, so the same two tasks
 * colliding tomorrow are a different pair and ask again. Nothing here knows that
 * rule — it falls out of the identity.
 */
class ConflictRepository(
    private val dao: ConflictDao,
    private val clock: Clock = Clock.System,
) {

    fun observeDismissed(from: LocalDate): Flow<Set<String>> =
        dao.observeFrom(from).map { rows -> rows.map { it.id }.toSet() }

    suspend fun dismiss(key: String, date: LocalDate) {
        dao.upsert(ConflictDismissal(id = key, date = date, createdAt = clock.now()))
    }

    /** Answers about blocks nobody will see again. */
    suspend fun prune(before: LocalDate) = dao.deleteBefore(before)
}
