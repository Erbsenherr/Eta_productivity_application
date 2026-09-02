package com.example.erik_iteration_2.data.repository

import com.example.erik_iteration_2.data.local.ContractDao
import com.example.erik_iteration_2.domain.contract.rejectionReason
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.ContractEffort
import com.example.erik_iteration_2.domain.model.ContractState
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZone).date

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
        val today = today()
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

    /** The evening's answer for one contract: kept, or breached and terminated. */
    suspend fun recordVerdict(contract: Contract, kept: Boolean, date: LocalDate) {
        val now = clock.now()
        contractDao.upsert(
            if (kept) {
                contract.copy(lastCheckedOn = date, updatedAt = now)
            } else {
                contract.copy(
                    state = ContractState.BROKEN,
                    lastCheckedOn = date,
                    closedAt = now,
                    updatedAt = now,
                )
            },
        )
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
                legacySince = today(),
                updatedAt = now,
            ),
        )
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
