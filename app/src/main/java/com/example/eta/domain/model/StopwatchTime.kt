package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * One time taken with the stopwatch in the "Gerade" box (step 56): how long a
 * task really took, measured rather than planned.
 *
 * It measures and does nothing else — no points, no billing, no growth. Its own
 * table, because it belongs to no plan: [itemId] and [blockId] are plain
 * columns, no foreign keys, so a time outlives the block it was taken on, and
 * the statistics file it under [name] — by name, like every per-task figure.
 *
 * [worked] is what had been counted at the last pause, [runningSince] when it
 * was last set going (null while paused) — the timer's shape, so the count is
 * right however long the app was closed. [endedAt] null: still being taken.
 */
@Entity(tableName = "stopwatch_times")
data class StopwatchTime(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val blockId: String,
    val name: String,
    val date: LocalDate,
    val worked: Duration = Duration.ZERO,
    val runningSince: Instant? = null,
    val endedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)
