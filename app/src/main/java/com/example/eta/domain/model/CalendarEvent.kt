package com.example.eta.domain.model

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * What has been decided about one calendar event.
 *
 * The point of writing it down at all is that **every** event has to be accounted
 * for: either it exists in the app as a real card, or the user has said it does
 * not concern them. Anything the calendar offers that is in neither state is by
 * definition new, and that is what makes "has something been added since
 * yesterday" answerable without asking Google.
 */
enum class EventDecision {
    /** Seen, not yet decided. The only state the two planning steps ask about. */
    PENDING,

    /** Turned into a card; [CalendarEvent.itemId] says which. */
    IMPORTED,

    /** Deliberately none of the app's business — a foreign calendar, a birthday. */
    IGNORED,
}

/**
 * One occurrence of a Google Calendar event, as the app last saw it.
 *
 * A cache with a decision attached, not a second planner: what the user actually
 * checks off is still an [Item] with a [PlannedBlock]. This row is what links the
 * two back to the calendar, so a second sync recognises "this event again" rather
 * than laying the appointment down a second time.
 *
 * The primary key is derived from the remote identity — calendar plus event id —
 * rather than random, which is what makes re-syncing idempotent by construction.
 * Recurring events are fetched already expanded (`singleEvents`), so each
 * occurrence arrives with an id of its own and one row means one appointment on
 * one day.
 */
@Entity(
    tableName = "calendar_events",
    indices = [Index("date"), Index("decision"), Index("itemId")],
)
data class CalendarEvent(
    @PrimaryKey val id: String,
    val calendarId: String,
    val eventId: String,
    val title: String,
    val date: LocalDate,
    /** For an all-day event this is only a suggestion; see [allDay]. */
    val start: LocalTime,
    val duration: Duration,
    /**
     * Google gave a date but no times.
     *
     * `Planungsphase.md` asks that the planner not silently swallow a whole day
     * for one of these: the event is offered with a suggested hour that the user
     * has to confirm, rather than being laid down across the morning routine.
     */
    val allDay: Boolean,
    /** The event's own `updated` stamp, so a changed appointment can be spotted. */
    val remoteUpdatedAt: Instant?,
    val decision: EventDecision,
    /** The card this became, for [EventDecision.IMPORTED]. */
    val itemId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    @get:Ignore
    val isPending: Boolean get() = decision == EventDecision.PENDING

    companion object {
        /** The remote identity, flattened into one key. */
        fun idOf(calendarId: String, eventId: String): String = "$calendarId|$eventId"
    }
}
