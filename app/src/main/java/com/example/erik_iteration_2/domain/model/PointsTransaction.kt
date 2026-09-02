package com.example.erik_iteration_2.domain.model

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Instant
import java.util.UUID

/** Where a points movement came from. */
enum class PointsReason {
    /** Evening harvest over the blocks completed that day. */
    HARVEST,
    CONTRACT,
    LEGACY_CONTRACT,

    /** The weekly 30% devaluation. */
    INFLATION,

    /** More than two unplanned hours in a day. */
    UNPLANNED_TIME,

    /** Free time given up, which pays. */
    FREE_TIME_FORGONE,

    /** Reward time claimed via a Custom Spend. */
    SPEND,
    MANUAL,
}

/**
 * One movement on the points account.
 *
 * The balance is the sum of this ledger rather than a stored number, so weekly
 * inflation, contract payouts and spends stay auditable instead of silently
 * overwriting each other.
 */
@Entity(
    tableName = "points_transactions",
    indices = [Index("occurredAt")],
)
data class PointsTransaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    /** Positive earns, negative spends. */
    val amount: Double,
    val reason: PointsReason,
    val occurredAt: Instant,
    val note: String? = null,
    val sourceBlockId: String? = null,
)
