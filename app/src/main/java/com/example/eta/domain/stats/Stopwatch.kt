package com.example.eta.domain.stats

import com.example.eta.domain.model.StopwatchTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** What has been counted at [now]: the pauses left out, however long the app was closed. */
fun StopwatchTime.elapsed(now: Instant): Duration =
    worked + (runningSince?.let { (now - it).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO)

val StopwatchTime.isRunning: Boolean get() = endedAt == null && runningSince != null

fun StopwatchTime.paused(now: Instant): StopwatchTime =
    if (runningSince == null) this else copy(worked = elapsed(now), runningSince = null, updatedAt = now)

fun StopwatchTime.resumed(now: Instant): StopwatchTime =
    if (runningSince != null || endedAt != null) this else copy(runningSince = now, updatedAt = now)

/** "Beenden": what was counted is the time, to the second. */
fun StopwatchTime.ended(now: Instant): StopwatchTime {
    val counted = elapsed(now)
    return copy(
        worked = counted.inWholeSeconds.seconds,
        runningSince = null,
        endedAt = now,
        updatedAt = now,
    )
}

/**
 * One task's times, for the Statistiken tab: how often it was measured, the
 * average, the shortest and the longest — and the times themselves, newest
 * first, to be corrected or deleted one by one.
 */
data class StopwatchSummary(
    val name: String,
    val times: List<StopwatchTime>,
) {
    val count: Int get() = times.size
    val average: Duration get() = times.fold(Duration.ZERO) { sum, t -> sum + t.worked } / count
    val shortest: Duration get() = times.minOf { it.worked }
    val longest: Duration get() = times.maxOf { it.worked }
}

/**
 * Every task ever measured, **alphabetically** — by its normalized name, as the
 * other per-task figures are, and called by the name it was last measured
 * under. Only finished times; one still being taken has no length yet.
 */
fun stopwatchSummaries(times: List<StopwatchTime>): List<StopwatchSummary> =
    times
        .filter { it.endedAt != null }
        .groupBy { it.name.trim().lowercase() }
        .values
        .map { group ->
            val sorted = group.sortedWith(compareByDescending<StopwatchTime> { it.date }.thenByDescending { it.createdAt })
            StopwatchSummary(name = sorted.first().name.trim(), times = sorted)
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
