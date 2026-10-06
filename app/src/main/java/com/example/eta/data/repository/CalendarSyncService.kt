package com.example.eta.data.repository

import android.app.PendingIntent
import android.content.Intent
import com.example.eta.data.calendar.AuthOutcome
import com.example.eta.data.calendar.GoogleAuth
import java.io.IOException
import kotlinx.datetime.LocalDate

/** What a sync ended in. */
sealed interface SyncOutcome {
    data class Ok(val summary: CalendarSyncSummary) : SyncOutcome

    /**
     * Google wants to show the user an account picker or a consent screen.
     *
     * Handed up rather than resolved here: only an Activity can launch a
     * `PendingIntent`, and a service that tried would have to hold one.
     */
    data class NeedsConsent(val pendingIntent: PendingIntent) : SyncOutcome

    data class Failed(val reason: String) : SyncOutcome

    /** No account connected. Not a failure — the ordinary state before setup. */
    data object NotConnected : SyncOutcome
}

/**
 * One round of talking to Google: a token, the calendar list, then the events.
 *
 * Keeps the two halves apart on purpose. [CalendarRepository] knows about rows
 * and decisions and nothing about Play Services; [GoogleAuth] knows about tokens
 * and nothing about calendars. This is the only place that needs both, and it is
 * also the only place that has to turn three different kinds of failure — no
 * grant, no network, a refused API — into something a screen can say.
 */
class CalendarSyncService(
    private val auth: GoogleAuth,
    private val repository: CalendarRepository,
) {

    /**
     * Connects for the first time, or re-connects: asks for the grant, then reads
     * the calendar list.
     *
     * The events are not fetched here — which calendars count has not been
     * answered yet, and reading everything only to throw most of it away would be
     * a slow way to start.
     */
    suspend fun connect(): SyncOutcome = withToken { token ->
        val calendars = repository.refreshCalendars(token)
        SyncOutcome.Ok(CalendarSyncSummary(calendars = calendars, events = 0, pending = 0))
    }

    /**
     * The consent screen's answer.
     *
     * Reads the calendar list with the token it came back with, which is what
     * turns "granted" into "connected" — the list is the record of the connection,
     * so a grant that never fetched one would leave the app looking unlinked.
     */
    suspend fun completeConsent(data: Intent?): SyncOutcome =
        when (val outcome = auth.tokenFrom(data)) {
            is AuthOutcome.Ok -> guarded {
                val calendars = repository.refreshCalendars(outcome.accessToken)
                SyncOutcome.Ok(
                    CalendarSyncSummary(calendars = calendars, events = 0, pending = 0),
                )
            }

            is AuthOutcome.NeedsConsent -> SyncOutcome.NeedsConsent(outcome.pendingIntent)
            is AuthOutcome.Failed -> SyncOutcome.Failed(outcome.reason)
        }

    /** Forgets the account. Nothing is asked of Google; see the repository. */
    suspend fun disconnect() = repository.disconnect()

    /**
     * Reads the switched-on calendars over [from]..[to].
     *
     * Silent when nothing is connected: the planning steps call this on the way
     * in, and a user who has never linked an account should not meet an error for
     * a feature they are not using.
     */
    suspend fun refresh(from: LocalDate, to: LocalDate): SyncOutcome {
        if (!repository.isConnected()) return SyncOutcome.NotConnected
        return withToken { token ->
            val calendars = repository.refreshCalendars(token)
            val events = repository.syncEvents(token, from, to)
            SyncOutcome.Ok(
                CalendarSyncSummary(
                    calendars = calendars,
                    events = events,
                    pending = repository.pendingFor(from, to).size,
                ),
            )
        }
    }

    private suspend fun withToken(block: suspend (String) -> SyncOutcome): SyncOutcome =
        when (val outcome = auth.token()) {
            is AuthOutcome.Ok -> guarded { block(outcome.accessToken) }
            is AuthOutcome.NeedsConsent -> SyncOutcome.NeedsConsent(outcome.pendingIntent)
            is AuthOutcome.Failed -> SyncOutcome.Failed(outcome.reason)
        }

    /**
     * Network failures become sentences rather than crashes.
     *
     * A sync runs on the way into a planning phase, and a phase that cannot be
     * entered because the flat's wifi is down would be the feature holding the
     * whole app hostage.
     */
    private suspend fun guarded(block: suspend () -> SyncOutcome): SyncOutcome =
        try {
            block()
        } catch (error: IOException) {
            SyncOutcome.Failed(error.message ?: "Keine Verbindung zu Google.")
        } catch (error: Exception) {
            SyncOutcome.Failed(error.message ?: "Der Kalender konnte nicht gelesen werden.")
        }
}
