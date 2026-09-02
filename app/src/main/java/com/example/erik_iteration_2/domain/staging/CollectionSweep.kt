package com.example.erik_iteration_2.domain.staging

import com.example.erik_iteration_2.domain.model.Item
import com.example.erik_iteration_2.domain.model.Stage
import kotlin.time.Instant
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** A ToDo may linger this long in the Sammelliste before it gets banned. */
const val COLLECTION_TIMEOUT_MONTHS = 1

/** How long a banned ToDo stays on the Sperrliste and cannot be re-created. */
const val SPERRLISTE_LOCK_MONTHS = 6

/** How far ahead of the ban a ToDo is flagged as critical on the dashboard. */
const val CRITICAL_WARNING_DAYS = 7

/**
 * Items that entered the Sammelliste at or before this instant are overdue.
 * Used to push the filtering down into the query instead of loading every item.
 */
fun collectionTimeoutThreshold(now: Instant, timeZone: TimeZone): Instant =
    now.minus(DateTimePeriod(months = COLLECTION_TIMEOUT_MONTHS), timeZone)

/**
 * True once the item has sat in the Sammelliste past the timeout.
 *
 * Measured from [Item.enteredCollectionAt], not [Item.updatedAt] — editing a ToDo
 * must not buy it another month.
 */
fun Item.isStaleInCollection(now: Instant, timeZone: TimeZone): Boolean {
    if (stage != Stage.COLLECTION) return false
    val entered = enteredCollectionAt ?: return false
    val deadline = entered.plus(DateTimePeriod(months = COLLECTION_TIMEOUT_MONTHS), timeZone)
    return now >= deadline
}

/**
 * The date this ToDo would be banned onto the Sperrliste, or null when the rule
 * does not apply to it.
 */
fun Item.banDate(timeZone: TimeZone): LocalDate? {
    if (stage != Stage.COLLECTION) return null
    val entered = enteredCollectionAt ?: return null
    return entered
        .plus(DateTimePeriod(months = COLLECTION_TIMEOUT_MONTHS), timeZone)
        .toLocalDateTime(timeZone)
        .date
}

/** Days left before the ban; negative once overdue, null when not applicable. */
fun Item.daysUntilBan(now: Instant, timeZone: TimeZone): Int? {
    val ban = banDate(timeZone) ?: return null
    return now.toLocalDateTime(timeZone).date.daysUntil(ban)
}

/**
 * Shown in dashboard box 0. "Kritisch" means about to expire *into the Sperrliste* —
 * the rule this warning hangs off in Konzept.md — not about to miss a deadline,
 * which box 3 covers separately.
 */
fun Item.isCriticalInCollection(now: Instant, timeZone: TimeZone): Boolean {
    val days = daysUntilBan(now, timeZone) ?: return false
    return days <= CRITICAL_WARNING_DAYS
}

/**
 * ToDos that entered the Sammelliste at or before this instant are within the
 * warning window. Lets the dashboard query filter in SQL.
 */
fun criticalThreshold(now: Instant, timeZone: TimeZone): Instant =
    collectionTimeoutThreshold(now, timeZone)
        .plus(DateTimePeriod(days = CRITICAL_WARNING_DAYS), timeZone)

/** Moves the item onto the Sperrliste, banned from re-creation for half a year. */
fun Item.lockedForSperrliste(now: Instant, timeZone: TimeZone): Item = copy(
    stage = Stage.LOCKED,
    lockedUntil = now.plus(DateTimePeriod(months = SPERRLISTE_LOCK_MONTHS), timeZone),
    updatedAt = now,
)
