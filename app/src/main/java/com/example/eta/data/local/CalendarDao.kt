package com.example.eta.data.local

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.example.eta.domain.model.CalendarEvent
import com.example.eta.domain.model.CalendarSource
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

@Dao
interface CalendarDao {

    // --- the calendars themselves -------------------------------------------

    @Upsert
    suspend fun upsertSources(sources: List<CalendarSource>)

    @Upsert
    suspend fun upsertSource(source: CalendarSource)

    @Query("SELECT * FROM calendar_sources ORDER BY isPrimary DESC, displayName")
    fun observeSources(): Flow<List<CalendarSource>>

    @Query("SELECT * FROM calendar_sources")
    suspend fun findSources(): List<CalendarSource>

    @Query("SELECT * FROM calendar_sources WHERE enabled = 1")
    suspend fun findEnabledSources(): List<CalendarSource>

    @Query("SELECT COUNT(*) FROM calendar_sources")
    suspend fun countSources(): Int

    /** Calendars Google no longer offers. Their events go with them, in the repository. */
    @Query("DELETE FROM calendar_sources WHERE id NOT IN (:keep)")
    suspend fun deleteSourcesNotIn(keep: List<String>): Int

    @Query("DELETE FROM calendar_sources")
    suspend fun clearSources()

    // --- the events ----------------------------------------------------------

    @Upsert
    suspend fun upsertEvents(events: List<CalendarEvent>)

    @Upsert
    suspend fun upsertEvent(event: CalendarEvent)

    @Query("SELECT * FROM calendar_events WHERE date BETWEEN :from AND :to")
    suspend fun findForRange(from: LocalDate, to: LocalDate): List<CalendarEvent>

    /**
     * What a planning step asks about: everything still undecided in its stretch.
     *
     * Ordered by day and hour rather than by when it arrived — the screen reads
     * as a run of days, and an appointment's place in one is its time.
     */
    @Query(
        """
        SELECT * FROM calendar_events
        WHERE decision = 'PENDING' AND date BETWEEN :from AND :to
        ORDER BY date, start
        """,
    )
    fun observePending(from: LocalDate, to: LocalDate): Flow<List<CalendarEvent>>

    @Query(
        """
        SELECT * FROM calendar_events
        WHERE decision = 'PENDING' AND date BETWEEN :from AND :to
        """,
    )
    suspend fun findPending(from: LocalDate, to: LocalDate): List<CalendarEvent>

    /** The "Ignorierte Events" list, newest day first. */
    @Query("SELECT * FROM calendar_events WHERE decision = 'IGNORED' ORDER BY date DESC, start")
    fun observeIgnored(): Flow<List<CalendarEvent>>

    /**
     * Clears out events of a stretch that the calendar no longer has.
     *
     * Only the undecided ones. An ignored event has to stay ignored — otherwise a
     * hiccup in a sync would offer it again — and an imported one has become a
     * card the user may already have worked from.
     */
    @Query(
        """
        DELETE FROM calendar_events
        WHERE date BETWEEN :from AND :to
          AND decision = 'PENDING'
          AND id NOT IN (:keep)
        """,
    )
    suspend fun deletePendingNotIn(from: LocalDate, to: LocalDate, keep: List<String>): Int

    @Query("DELETE FROM calendar_events WHERE calendarId NOT IN (:keep)")
    suspend fun deleteEventsOfCalendarsNotIn(keep: List<String>): Int

    @Query("DELETE FROM calendar_events")
    suspend fun clearEvents()
}
