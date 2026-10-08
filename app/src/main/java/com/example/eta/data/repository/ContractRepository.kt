package com.example.eta.data.repository

import com.example.eta.data.local.ContractDao
import com.example.eta.domain.contract.abandonedContract
import com.example.eta.domain.contract.editedContract
import com.example.eta.domain.contract.probationAnswered
import com.example.eta.domain.contract.rejectionReason
import com.example.eta.domain.contract.restartedContract
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.ContractEffort
import com.example.eta.domain.model.ContractState
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Signing either produces a contract or says why it cannot be signed. */
sealed interface SignResult {
    data class Signed(val contract: Contract) : SignResult
    data class Refused(val reason: String) : SignResult
}

class ContractRepository(
    private val contractDao: ContractDao,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    fun observeAll(): Flow<List<Contract>> = contractDao.observeAll()

    suspend fun findRunning(): List<Contract> = contractDao.findRunning()

    /**
     * Signs a contract, or refuses with the reason.
     *
     * The rules live in `domain/contract`, so the refusal a screen shows and the
     * one a test asserts are the same sentence.
     */
    suspend fun sign(
        slot: Int,
        title: String,
        conditions: String,
        breachDefinition: String,
        effort: ContractEffort,
        signature: String,
        endsOn: LocalDate,
    ): SignResult {
        val today = clock.todayIn(timeZone)
        val existing = contractDao.findAll()

        rejectionReason(
            contracts = existing,
            slot = slot,
            signedOn = today,
            endsOn = endsOn,
            conditions = conditions,
            signature = signature,
            today = today,
        )?.let { return SignResult.Refused(it) }

        val contract = Contract.new(
            slot = slot,
            title = title.ifBlank { conditions.take(40) },
            conditions = conditions,
            breachDefinition = breachDefinition,
            effort = effort,
            signature = signature,
            signedOn = today,
            endsOn = endsOn,
            now = clock.now(),
        )
        contractDao.upsert(contract)
        return SignResult.Signed(contract)
    }

    /**
     * The evening's answer for one contract.
     *
     * Three outcomes, not two. Kept changes nothing but the date. A breach ends
     * the contract and locks its slot — unless [keepServing], which is the user
     * deciding to hold the promise anyway: the contract stays in the slot, keeps
     * being asked about every evening, and pays nothing for as long as the lock
     * runs.
     *
     * A contract **already on probation** stays on it whatever the answer. It
     * is broken already; the consequence is running, and marking it broken a
     * second time would move nothing and would lose the fact that it is still
     * being served. The answer is counted, though — see [probationAnswered]:
     * two weeks of "gehalten" in a row are what lets it be put back into force.
     */
    suspend fun recordVerdict(
        contract: Contract,
        kept: Boolean,
        date: LocalDate,
        keepServing: Boolean = false,
    ) {
        val now = clock.now()
        val answered = contract.copy(lastCheckedOn = date, updatedAt = now)
        contractDao.upsert(
            when {
                contract.isOnProbation -> probationAnswered(contract, kept, date, now)
                kept -> answered
                keepServing -> answered.copy(
                    state = ContractState.PROBATION,
                    closedAt = null,
                    probationKeptSince = null,
                )
                else -> answered.copy(state = ContractState.BROKEN, closedAt = now)
            },
        )
    }

    /**
     * The promise is taken up again: same words, term at zero. Earned either by
     * the month being up or by two kept weeks in a row — see [restartedContract].
     *
     * Returns false when neither is the case — the rules live in
     * `domain/contract`, so what a screen offers and what a test asserts are the
     * same code.
     */
    suspend fun restartProbation(contract: Contract): Boolean {
        val restarted = restartedContract(contract, clock.todayIn(timeZone), clock.now()) ?: return false
        contractDao.upsert(restarted)
        return true
    }

    /** The other answer: the promise is let go, and the slot comes free. */
    suspend fun abandonProbation(contract: Contract): Boolean {
        val abandoned = abandonedContract(contract, clock.todayIn(timeZone), clock.now()) ?: return false
        contractDao.upsert(abandoned)
        return true
    }

    /**
     * Frees the slot and drops the payout to a fifth. The contract keeps running:
     * a legacy contract still has to be kept, it just no longer costs a slot.
     */
    suspend fun upgradeToLegacy(contract: Contract) {
        val now = clock.now()
        contractDao.upsert(
            contract.copy(
                state = ContractState.LEGACY,
                slot = null,
                legacySince = clock.todayIn(timeZone),
                updatedAt = now,
            ),
        )
    }

    /**
     * Changing a running contract's wording. Once, and the term restarts.
     *
     * Only the text: the effort, and with it the daily payout, stays where it was
     * — an edit that could raise the rate would make this a way to pay yourself
     * more for a promise already half-served. The term keeps its **length** and
     * loses its run-up, so a contract two weeks in is back at zero and needs a
     * full month again before it can become legacy.
     *
     * Returns false when the contract may not be changed; [editRefusal] in the
     * dialog is what says which of the reasons applies.
     */
    suspend fun editWording(
        contract: Contract,
        title: String,
        conditions: String,
        breachDefinition: String,
    ): Boolean {
        val edited = editedContract(
            contract = contract,
            title = title,
            conditions = conditions,
            breachDefinition = breachDefinition,
            today = clock.todayIn(timeZone),
            now = clock.now(),
        ) ?: return false

        contractDao.upsert(edited)
        return true
    }

    /** Extending a contract that ran its term — no breach, no lock. */
    suspend fun extend(contract: Contract, newEnd: LocalDate) {
        contractDao.upsert(contract.copy(endsOn = newEnd, updatedAt = clock.now()))
    }

    /** Ending a contract that ran its term. Deliberate, so the slot stays free. */
    suspend fun fulfil(contract: Contract) {
        val now = clock.now()
        contractDao.upsert(
            contract.copy(state = ContractState.FULFILLED, closedAt = now, updatedAt = now),
        )
    }

    suspend fun update(contract: Contract) {
        contractDao.upsert(contract.copy(updatedAt = clock.now()))
    }
}
