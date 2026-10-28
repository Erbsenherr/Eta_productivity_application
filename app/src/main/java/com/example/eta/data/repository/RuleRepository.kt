package com.example.eta.data.repository

import com.example.eta.data.local.RuleDao
import com.example.eta.domain.model.Rule
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow

/** The rules of the Regelwerk — see [Rule]. */
class RuleRepository(
    private val dao: RuleDao,
    private val clock: Clock = Clock.System,
) {

    fun observe(): Flow<List<Rule>> = dao.observeAll()

    /** Saves a rule: a new one, last in the list, for a null [id]; otherwise the one it names. */
    suspend fun save(id: String?, question: String, answer: String) {
        val now = clock.now()
        val existing = id?.let { dao.find(it) }
        dao.upsert(
            existing?.copy(question = question, answer = answer, updatedAt = now)
                ?: Rule(
                    question = question,
                    answer = answer,
                    position = dao.lastPosition() + 1,
                    createdAt = now,
                    updatedAt = now,
                ),
        )
    }

    suspend fun delete(id: String) = dao.delete(id)
}
