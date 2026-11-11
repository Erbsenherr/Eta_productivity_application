package com.example.eta.data.repository

import com.example.eta.data.local.RuleDao
import com.example.eta.domain.model.ItemRole
import com.example.eta.domain.model.Rule
import com.example.eta.domain.model.RuleBreach
import com.example.eta.domain.rules.withConsequenceNote
import com.example.eta.domain.rules.withoutConsequenceNote
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** What a rule says, as the editor hands it over. */
data class RuleDraft(
    val question: String,
    val answer: String,
    val exception: String? = null,
    val consequence: String? = null,
    val consequenceOnFreeTime: Boolean = false,
)

/** The rules of the Regelwerk — see [Rule] — and the breaches reported (step 57). */
class RuleRepository(
    private val dao: RuleDao,
    private val planRepository: PlanRepository,
    private val clock: Clock = Clock.System,
) {

    fun observe(): Flow<List<Rule>> = dao.observeAll()

    fun observeBreaches(): Flow<List<RuleBreach>> = dao.observeBreaches()

    /** Saves a rule: a new one, last in the list, for a null [id]; otherwise the one it names. */
    suspend fun save(id: String?, draft: RuleDraft) {
        val now = clock.now()
        val existing = id?.let { dao.find(it) }
        val consequence = draft.consequence?.trim()?.ifBlank { null }
        val fields = { rule: Rule ->
            rule.copy(
                question = draft.question.trim(),
                answer = draft.answer.trim(),
                exception = draft.exception?.trim()?.ifBlank { null },
                consequence = consequence,
                // Nothing to tie to the free time without a consequence to say.
                consequenceOnFreeTime = draft.consequenceOnFreeTime && consequence != null,
                updatedAt = now,
            )
        }
        dao.upsert(
            if (existing != null) {
                fields(existing)
            } else {
                fields(
                    Rule(
                        question = "",
                        answer = "",
                        position = dao.lastPosition() + 1,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            },
        )
    }

    /** A rule and every breach reported of it. */
    suspend fun delete(id: String) {
        dao.deleteBreachesOf(id)
        dao.delete(id)
    }

    /**
     * "Regelverstoß melden": [ruleId] broken on [date]. A rule tied to the free
     * time writes its consequence into the note of that day's free time —
     * once, however often it is reported.
     */
    suspend fun report(ruleId: String, date: LocalDate) {
        val rule = dao.find(ruleId) ?: return
        val now = clock.now()
        dao.upsertBreach(RuleBreach(ruleId = ruleId, date = date, createdAt = now, updatedAt = now))
        val consequence = rule.consequence ?: return
        if (!rule.consequenceOnFreeTime) return
        planRepository.addBlocks(
            planRepository.findDay(date)
                .filter { it.item.role == ItemRole.FREE_TIME }
                .map { it.block }
                .mapNotNull { block ->
                    val note = withConsequenceNote(block.note, consequence)
                    block.takeIf { note != it.note }?.copy(note = note, updatedAt = now)
                },
        )
    }

    /**
     * A report taken back. The note's line goes with it unless the same rule
     * stands reported for that day once more.
     */
    suspend fun withdraw(breachId: String) {
        val breach = dao.findBreach(breachId) ?: return
        dao.deleteBreach(breachId)
        val rule = dao.find(breach.ruleId) ?: return
        val consequence = rule.consequence ?: return
        if (!rule.consequenceOnFreeTime) return
        if (dao.breachesOn(breach.date).any { it.ruleId == rule.id }) return
        val now = clock.now()
        planRepository.addBlocks(
            planRepository.findDay(breach.date)
                .filter { it.item.role == ItemRole.FREE_TIME }
                .map { it.block }
                .mapNotNull { block ->
                    val note = withoutConsequenceNote(block.note, consequence)
                    block.takeIf { note != it.note }?.copy(note = note, updatedAt = now)
                },
        )
    }
}
