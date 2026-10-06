package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.util.UUID

/** What a contract asks of the user per day, in points. */
enum class ContractEffort(val pointsPerDay: Double) {
    LEICHT(0.5),
    MITTEL(1.0),
    SCHWER(1.5),
}

enum class ContractState {
    /** Running and holding one of the three slots. */
    ACTIVE,

    /** Upgraded after a month: holds no slot, pays a fifth, still has to be kept. */
    LEGACY,

    /** Ran its term and was ended deliberately. No slot lock. */
    FULFILLED,

    /** Breached and let go. Locks its slot for a month from the day it was signed. */
    BROKEN,

    /**
     * Breached, and taken up again anyway — served out without pay.
     *
     * The user's own answer to a breach, and the interesting one: the promise was
     * not kept, but giving it up is not the only thing to do about that. The
     * contract stays in its slot, the evening keeps asking whether it was held,
     * and it pays **nothing** for as long as the lock runs. That is what makes it
     * different from [ACTIVE] — the habit continues while the consequence does
     * too, rather than one cancelling the other.
     *
     * It holds its slot **until the user decides**, not merely until the lock
     * expires. Freeing the slot the moment the month is up would let a new
     * contract be signed into it while this one is still being served every
     * evening, and then there would be two things in one slot.
     */
    PROBATION,
}

/** Three contracts at a time — legacy ones do not count against this. */
const val CONTRACT_SLOTS = 3

/** The shortest term the concept allows. */
val MIN_CONTRACT_TERM = DatePeriod(days = 14)

/** How long a contract has to run before it may be upgraded to a legacy one. */
val LEGACY_QUALIFYING_TERM = DatePeriod(months = 1)

/** How long a breach blocks the slot, counted from the signing date. */
val BREACH_LOCK = DatePeriod(months = 1)

/** A legacy contract pays a fifth of what it did as a full one. */
const val LEGACY_SHARE = 0.2

/**
 * A contract with oneself.
 *
 * The signature and date are part of the record rather than decoration: the
 * concept builds the whole mechanism on having promised something, and a breach
 * costs the slot for a month.
 */
@Entity(
    tableName = "contracts",
    indices = [Index("state"), Index("slot")],
)
data class Contract(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** 0 until [CONTRACT_SLOTS] while it holds one; a legacy contract carries null. */
    val slot: Int?,
    val title: String,
    /** Was verlangt der Vertrag? */
    val conditions: String,
    /** Was ist ein Vertragsbruch? */
    val breachDefinition: String,
    val effort: ContractEffort,
    val signature: String,
    val signedOn: LocalDate,
    /** End of the agreed term. A legacy contract runs on past it. */
    val endsOn: LocalDate,
    val state: ContractState,
    val legacySince: LocalDate? = null,
    val closedAt: Instant? = null,
    /** The last day the evening check was answered, so it is only asked once. */
    val lastCheckedOn: LocalDate? = null,
    /**
     * When the wording was changed, if it ever was.
     *
     * One change per contract, and this is the flag that enforces it. A contract
     * you may rewrite whenever it becomes inconvenient is not a promise, so the
     * allowance is exactly one — and it costs the term, which restarts from the
     * day of the change.
     */
    val editedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /**
     * Still owed an answer every evening.
     *
     * A contract on probation is: that is the whole point of choosing it over
     * letting the promise go. It simply is not paid for the answer.
     */
    @get:Ignore
    val isRunning: Boolean get() = state == ContractState.ACTIVE ||
        state == ContractState.LEGACY ||
        state == ContractState.PROBATION

    /** Broken, and being served out anyway. */
    @get:Ignore
    val isOnProbation: Boolean get() = state == ContractState.PROBATION

    /**
     * What one kept day is worth. Legacy contracts pay a fifth, a contract on
     * probation nothing at all — it is still a broken promise, and being paid for
     * keeping it afterwards would make the breach free.
     */
    @get:Ignore
    val dailyPayout: Double
        get() = when (state) {
            ContractState.LEGACY -> effort.pointsPerDay * LEGACY_SHARE
            ContractState.ACTIVE -> effort.pointsPerDay
            else -> 0.0
        }

    /**
     * The slot stays blocked until a month after **signing**, not after breaking.
     * That is what `Selbstverträge.md` says, and it means a contract broken late
     * in its term frees its slot sooner than one broken early — the cost of a
     * breach is deliberately tied to how long ago the promise was made.
     *
     * The same for a contract on probation: continuing to serve it neither
     * shortens the lock nor lengthens it. The choice is about the habit, not
     * about the price.
     */
    @get:Ignore
    val slotLockedUntil: LocalDate?
        get() = if (
            (state == ContractState.BROKEN || state == ContractState.PROBATION) && slot != null
        ) {
            signedOn.plus(BREACH_LOCK)
        } else {
            null
        }

    /**
     * A probation whose month is up: the user owes the slot a decision.
     *
     * Until it is given, the slot stays held — see [ContractState.PROBATION].
     */
    fun probationDecisionDue(today: LocalDate): Boolean =
        state == ContractState.PROBATION && slotLockedUntil?.let { today >= it } == true

    /** Ran its full term and is waiting to be extended, changed or ended. */
    fun isExpiring(today: LocalDate): Boolean =
        state == ContractState.ACTIVE && today >= endsOn

    /** Has run uninterrupted long enough to become a legacy contract. */
    fun canUpgradeToLegacy(today: LocalDate): Boolean =
        state == ContractState.ACTIVE && today >= signedOn.plus(LEGACY_QUALIFYING_TERM)

    /**
     * Whether the wording may still be changed.
     *
     * A legacy contract may not: it already got its reduction by running a month,
     * and rewriting what it asks would make that month meaningless. Neither may a
     * broken or fulfilled one — those are closed.
     */
    @get:Ignore
    val isEditable: Boolean get() = state == ContractState.ACTIVE && editedAt == null

    /** Whether the evening still owes this contract its question on [date]. */
    fun needsCheckOn(date: LocalDate): Boolean =
        isRunning && lastCheckedOn != date && date >= signedOn

    companion object {
        fun new(
            slot: Int,
            title: String,
            conditions: String,
            breachDefinition: String,
            effort: ContractEffort,
            signature: String,
            signedOn: LocalDate,
            endsOn: LocalDate,
            now: Instant,
        ) = Contract(
            slot = slot,
            title = title,
            conditions = conditions,
            breachDefinition = breachDefinition,
            effort = effort,
            signature = signature,
            signedOn = signedOn,
            endsOn = endsOn,
            state = ContractState.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )
    }
}
