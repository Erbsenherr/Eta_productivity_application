package com.example.erik_iteration_2.domain.contract

import com.example.erik_iteration_2.domain.model.CONTRACT_SLOTS
import com.example.erik_iteration_2.domain.model.Contract
import com.example.erik_iteration_2.domain.model.ContractState
import com.example.erik_iteration_2.domain.model.MIN_CONTRACT_TERM
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/** Why a slot cannot be used right now. */
sealed interface SlotStatus {
    data object Free : SlotStatus
    data class Taken(val contract: Contract) : SlotStatus

    /** Blocked by a breach until [until] — a new contract cannot simply replace it. */
    data class Locked(val until: LocalDate, val brokenContract: Contract) : SlotStatus
}

/**
 * The three slots and what each is doing.
 *
 * Locks are what make a breach cost something: without them the user could break
 * a contract and sign a fresh one the same evening, which is exactly what
 * `Selbstverträge.md` sets out to prevent.
 */
fun slotStatuses(contracts: List<Contract>, today: LocalDate): List<SlotStatus> =
    (0 until CONTRACT_SLOTS).map { slot ->
        val holder = contracts.firstOrNull {
            it.slot == slot && it.state == ContractState.ACTIVE
        }
        if (holder != null) return@map SlotStatus.Taken(holder)

        // The longest running lock wins; several breaches can pile up on one slot.
        val lock = contracts
            .filter { it.slot == slot && it.state == ContractState.BROKEN }
            .mapNotNull { broken -> broken.slotLockedUntil?.let { broken to it } }
            .filter { (_, until) -> today < until }
            .maxByOrNull { (_, until) -> until }

        if (lock == null) SlotStatus.Free else SlotStatus.Locked(lock.second, lock.first)
    }

fun freeSlots(contracts: List<Contract>, today: LocalDate): List<Int> =
    slotStatuses(contracts, today)
        .mapIndexedNotNull { slot, status -> slot.takeIf { status is SlotStatus.Free } }

/** Why a draft contract cannot be signed yet. Null when it can. */
fun rejectionReason(
    contracts: List<Contract>,
    slot: Int,
    signedOn: LocalDate,
    endsOn: LocalDate,
    conditions: String,
    signature: String,
    today: LocalDate,
): String? = when {
    conditions.isBlank() -> "Ohne Konditionen ist es kein Vertrag."
    signature.isBlank() -> "Es fehlt die Unterschrift."
    endsOn < signedOn.plus(MIN_CONTRACT_TERM) -> "Ein Vertrag läuft mindestens zwei Wochen."
    slot !in freeSlots(contracts, today) -> "Dieser Vertrags-Slot ist nicht frei."
    else -> null
}

/** Contracts the evening still has to ask about, in slot order, legacy ones last. */
fun contractsToCheck(contracts: List<Contract>, date: LocalDate): List<Contract> =
    contracts
        .filter { it.needsCheckOn(date) }
        .sortedWith(compareBy({ it.state != ContractState.ACTIVE }, { it.slot ?: Int.MAX_VALUE }))

/** Contracts whose term is up — mentioned in the last day's planning phase. */
fun expiringContracts(contracts: List<Contract>, today: LocalDate): List<Contract> =
    contracts.filter { it.isExpiring(today) }

/** Contracts that have run a month and may be upgraded. */
fun upgradableContracts(contracts: List<Contract>, today: LocalDate): List<Contract> =
    contracts.filter { it.canUpgradeToLegacy(today) }

/**
 * The one wording change a contract is allowed, applied.
 *
 * Returns null where it is not allowed — a legacy contract, a closed one, or one
 * that has already had its change. See [Contract.isEditable].
 *
 * Only the **text** moves. The effort, and with it the daily payout, stays: an
 * edit that could raise the rate would make this a way of paying yourself more
 * for a promise already half-served. What it costs is the run-up — the term keeps
 * its length and restarts from [today], so a contract two weeks in is back at
 * zero and needs a full month again before it can become legacy. That is the
 * rule, not a side effect: changing what you promised is making a new promise.
 */
fun editedContract(
    contract: Contract,
    title: String,
    conditions: String,
    breachDefinition: String,
    today: LocalDate,
    now: Instant,
): Contract? {
    if (!contract.isEditable) return null
    val termDays = contract.signedOn.daysUntil(contract.endsOn)
    return contract.copy(
        title = title.ifBlank { conditions.take(40) },
        conditions = conditions,
        breachDefinition = breachDefinition,
        signedOn = today,
        endsOn = today.plus(DatePeriod(days = termDays)),
        editedAt = now,
        updatedAt = now,
    )
}
