package com.example.eta.data.repository

import com.example.eta.data.calendar.GoogleCalendarApi
import com.example.eta.data.calendar.RemoteEvent
import com.example.eta.data.local.CalendarDao
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.model.CalendarSource
import com.example.eta.domain.model.EventDecision
import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** What one round of syncing produced. */
data class CalendarSyncSummary(
    val calendars: Int,
    val events: Int,
    val pending: Int,
)

/**
 * The calendars, their events, and what has been decided about each.
 *
 * Read-only about Google: nothing here ever writes to a calendar. What it does
 * own is the *decision* — every event is either a card in the app or explicitly
 * ignored, and that is the invariant the two planning steps lean on. An event in
 * neither state is by definition new, which is how "has something been added for
 * tomorrow" is answerable without asking Google what the app knew yesterday.
 */
class CalendarRepository(
    private val dao: CalendarDao,
    private val api: GoogleCalendarApi,
    private val clock: Clock = Clock.System,
) {

    fun observeSources(): Flow<List<CalendarSource>> = dao.observeSources()

    fun observePending(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>> =
        dao.observePending(from, to)

    fun observeIgnored(): Flow<List<CalendarEvent>> = dao.observeIgnored()

    /**
     * Whether a Google account has been connected.
     *
     * Read off the calendar list rather than a flag of its own: connecting always
     * fetches it, and disconnecting clears it, so the list *is* the record.
     * A second boolean could only disagree with it — the same reasoning that
     * keeps the weekly devaluation reading the ledger instead of a stored mark.
     */
    suspend fun isConnected(): Boolean = dao.countSources() > 0

    suspend fun setEnabled(source: CalendarSource, enabled: Boolean) {
        dao.upsertSource(source.copy(enabled = enabled, updatedAt = clock.now()))
    }

    /**
     * Refreshes the list of calendars.
     *
     * [CalendarSource.enabled] is the one field carried over from what is already
     * stored: it is the only one the user wrote, and a refresh must not quietly
     * switch a calendar back on. A calendar seen for the first time is on only if
     * it is the account's own primary one — a foreign or subscribed calendar
     * counting towards the day by default is exactly what the user asked to be
     * able to avoid.
     */
    suspend fun refreshCalendars(token: String): Int {
        val remote = api.calendars(token)
        if (remote.isEmpty()) return 0

        val known = dao.findSources().associateBy { it.id }
        val now = clock.now()
        dao.upsertSources(
            remote.map { calendar ->
                CalendarSource(
                    id = calendar.id,
                    displayName = calendar.displayName,
                    accountName = calendar.accountName ?: known[calendar.id]?.accountName,
                    isPrimary = calendar.isPrimary,
                    isForeign = calendar.isForeign,
                    enabled = known[calendar.id]?.enabled ?: calendar.isPrimary,
                    updatedAt = now,
                )
            },
        )
        val keep = remote.map { it.id }
        dao.deleteSourcesNotIn(keep)
        // A calendar that is gone takes its undecided events with it; anything
        // already imported has become a card and stands on its own.
        dao.deleteEventsOfCalendarsNotIn(keep)
        return remote.size
    }

    /**
     * Reads the switched-on calendars over a stretch of days and files what it
     * finds.
     *
     * Idempotent, and that is the whole design: the row id is the remote identity,
     * so the same appointment fetched twice lands on the same row. A row that
     * already carries a decision keeps it — only the *facts* are refreshed, so an
     * appointment moved in Google shows its new time without being offered for
     * import a second time.
     */
    suspend fun syncEvents(token: String, from: LocalDate, to: LocalDate): Int {
        val calendars = dao.findEnabledSources()
        if (calendars.isEmpty()) return 0

        val remote = calendars.flatMap { api.events(token, it.id, from, to) }
        val known = dao.findForRange(from, to).associateBy { it.id }
        val now = clock.now()

        dao.upsertEvents(remote.map { event -> merge(event, known[idOf(event)], now) })
        // What the calendar no longer offers and nobody has decided about is gone.
        dao.deletePendingNotIn(from, to, remote.map { idOf(it) })
        return remote.size
    }

    private fun idOf(event: RemoteEvent) = CalendarEvent.idOf(event.calendarId, event.eventId)

    private fun merge(
        event: RemoteEvent,
        existing: CalendarEvent?,
        now: kotlin.time.Instant,
    ): CalendarEvent = CalendarEvent(
        id = idOf(event),
        calendarId = event.calendarId,
        eventId = event.eventId,
        title = event.title,
        date = event.date,
        start = event.start,
        duration = event.duration,
        allDay = event.allDay,
        remoteUpdatedAt = event.updatedAt,
        // The two fields the user owns. Google has no opinion about either.
        decision = existing?.decision ?: EventDecision.PENDING,
        itemId = existing?.itemId,
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )

    suspend fun pendingFor(from: LocalDate, to: LocalDate): List<CalendarEvent> =
        dao.findPending(from, to)

    /** Filed under "geht mich nichts an" — and so no longer new. */
    suspend fun ignore(event: CalendarEvent) {
        dao.upsertEvent(
            event.copy(decision = EventDecision.IGNORED, updatedAt = clock.now()),
        )
    }

    /** Taking that back: the event is undecided again and will be offered afresh. */
    suspend fun undecide(event: CalendarEvent) {
        dao.upsertEvent(
            event.copy(decision = EventDecision.PENDING, itemId = null, updatedAt = clock.now()),
        )
    }

    /** The link that keeps a second sync from planning the same appointment twice. */
    suspend fun markImported(event: CalendarEvent, itemId: String) {
        dao.upsertEvent(
            event.copy(
                decision = EventDecision.IMPORTED,
                itemId = itemId,
                updatedAt = clock.now(),
            ),
        )
    }

    /**
     * Forgets the account.
     *
     * Only what was read from Google goes; the cards already made from it stay,
     * because they are the user's plan rather than Google's data. Revoking the
     * grant itself happens in the Google account settings, and the screen says so
     * rather than pretending a local delete reaches that far.
     */
    suspend fun disconnect() {
        dao.clearEvents()
        dao.clearSources()
    }
}
