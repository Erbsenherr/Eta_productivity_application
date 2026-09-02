package com.example.erik_iteration_2.domain.model

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

    /** Breached. Locks its slot for a month from the day it was signed. */
    BROKEN,
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
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    @get:Ignore
    val isRunning: Boolean get() = state == ContractState.ACTIVE || state == ContractState.LEGACY

    /** What one kept day is worth. Legacy contracts pay a fifth. */
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
     */
    @get:Ignore
    val slotLockedUntil: LocalDate?
        get() = if (state == ContractState.BROKEN && slot != null) {
            signedOn.plus(BREACH_LOCK)
        } else {
            null
        }

    /** Ran its full term and is waiting to be extended, changed or ended. */
    fun isExpiring(today: LocalDate): Boolean =
        state == ContractState.ACTIVE && today >= endsOn

    /** Has run uninterrupted long enough to become a legacy contract. */
    fun canUpgradeToLegacy(today: LocalDate): Boolean =
        state == ContractState.ACTIVE && today >= signedOn.plus(LEGACY_QUALIFYING_TERM)

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
