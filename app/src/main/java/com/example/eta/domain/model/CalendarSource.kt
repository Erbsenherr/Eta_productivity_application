package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlin.time.Instant

/**
 * One calendar the connected Google account can see, and whether it counts.
 *
 * A table rather than a single "sync on/off" switch, because one account already
 * holds several: the personal one, a work one, the German public holidays, a
 * timetable somebody subscribed to, a partner's calendar shared in. Which of
 * those describe the user's own day is a question only the user can answer, so
 * each carries its own [enabled].
 *
 * Rows are refreshed from Google on every connect; [enabled] is the one field
 * that survives a refresh, because it is the only one the user wrote.
 */
@Entity(tableName = "calendar_sources")
data class CalendarSource(
    /** Google's calendar id — an address for the primary one, a long id otherwise. */
    @PrimaryKey val id: String,
    val displayName: String,
    /** The account this calendar was read through. */
    val accountName: String?,
    /** The account's own main calendar. Switched on by default; the rest are not. */
    val isPrimary: Boolean,
    /**
     * Somebody else's: subscribed or shared in, and readable rather than owned.
     * Worth saying on the checkbox — these are the ones most often ignored.
     */
    val isForeign: Boolean,
    val enabled: Boolean,
    val updatedAt: Instant,
)
