package com.example.eta.domain.contract

import com.example.eta.domain.model.CONTRACT_SLOTS
import com.example.eta.domain.model.Contract
import com.example.eta.domain.model.ContractState
import com.example.eta.domain.model.MIN_CONTRACT_TERM
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

    /**
     * Blocked by a breach the user chose to go on serving, unpaid.
     *
     * Its own status rather than a flag on [Locked], because the slot is doing
     * something different: there is a contract in it, it is asked about every
     * evening, and it is drawn as a contract rather than as an empty red box.
     *
     * [decisionDue] is the month being up. The slot stays held either way — see
     * [ContractState.PROBATION] — but from that day the screen has a question to
     * put rather than a countdown to show.
     */
    data class Serving(
        val until: LocalDate,
        val contract: Contract,
        val decisionDue: Boolean,
    ) : SlotStatus
}

/**
 * The three slots and what each is doing.
 *
 * Locks are what make a breach cost something: without them the user could break
 * a contract and sign a fresh one the same evening, which is exactly what
 * `Selbstverträge.md` sets out to prevent.
 *
 * A contract being **served out** after a breach wins over every other reading of
 * its slot, and it holds the slot past the end of its own lock. Anything else
 * would free the slot for a new contract while the old one is still being
 * answered for every evening, and one slot would then hold two promises.
 */
fun slotStatuses(contracts: List<Contract>, today: LocalDate): List<SlotStatus> =
    (0 until CONTRACT_SLOTS).map { slot ->
        val inSlot = contracts.filter { it.slot == slot }

        inSlot.firstOrNull { it.state == ContractState.PROBATION }?.let { serving ->
            val until = serving.slotLockedUntil ?: serving.signedOn
            return@map SlotStatus.Serving(
                until = until,
                contract = serving,
                decisionDue = today >= until,
            )
        }

        val holder = inSlot.firstOrNull { it.state == ContractState.ACTIVE }
        if (holder != null) return@map SlotStatus.Taken(holder)

        // The longest running lock wins; several breaches can pile up on one slot.
        val lock = inSlot
            .filter { it.state == ContractState.BROKEN }
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

/** Probations whose month is up and which now owe the slot an answer. */
fun probationsAwaitingDecision(contracts: List<Contract>, today: LocalDate): List<Contract> =
    contracts.filter { it.probationDecisionDue(today) }

/**
 * Taking the promise up again, with the term back at zero.
 *
 * The same words, the same effort, the same signature — only the run-up is gone:
 * the contract starts today and runs for as long as it originally did, so it
 * needs a full month again before it can become legacy. That is the point rather
 * than a side effect, and it is the same rule a wording change already obeys: a
 * promise made again is a new promise, whatever it says.
 *
 * `editedAt` is deliberately **not** cleared. The one wording change is an
 * allowance per contract, and breaking it must not hand out a second.
 *
 * Null unless the month is actually up: the choice exists because the
 * consequence has been served, and offering it earlier would be a way out of it.
 */
fun restartedContract(contract: Contract, today: LocalDate, now: Instant): Contract? {
    if (!contract.probationDecisionDue(today)) return null
    val termDays = contract.signedOn.daysUntil(contract.endsOn)
    return contract.copy(
        state = ContractState.ACTIVE,
        signedOn = today,
        endsOn = today.plus(DatePeriod(days = termDays)),
        closedAt = null,
        updatedAt = now,
    )
}

/**
 * Letting the promise go after having served it out.
 *
 * It becomes an ordinary broken contract and joins the list of them. The slot
 * comes free by itself: the lock it held ran out before this choice was offered
 * at all, and what was keeping the slot was the serving, which stops here.
 */
fun abandonedContract(contract: Contract, today: LocalDate, now: Instant): Contract? {
    if (!contract.probationDecisionDue(today)) return null
    return contract.copy(
        state = ContractState.BROKEN,
        closedAt = now,
        updatedAt = now,
    )
}

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
