package com.example.eta.domain.planning

import kotlinx.datetime.LocalDateTime

/**
 * What the daily phase still owes, as the dashboard reports it.
 *
 * Two duties, not one: settle today, and have tomorrow planned. They are tracked
 * apart because planning ahead is encouraged — doing tomorrow at lunchtime must
 * not make the evening's settlement look done.
 */
data class DailyPhaseStatus(
    val todaySettled: Boolean,
    val tomorrowConfirmed: Boolean,
    val tomorrowHasBlocks: Boolean,
    val dueAt: LocalDateTime?,
    /**
     * When today's phase becomes owed: the planning time, on today's date.
     *
     * Different from [dueAt], which is the *next* planning strictly after now and
     * so has already moved on to tomorrow once this evening's time has passed.
     * Null means nothing says when — no setup — and reads as owed, because being
     * too loud is better than going quiet about a day that is never settled.
     */
    val owedFrom: LocalDateTime? = null,
) {
    val isComplete: Boolean get() = todaySettled && tomorrowConfirmed

    /** Tomorrow already has a plan waiting to be waved through. */
    val tomorrowReadyToConfirm: Boolean get() = !tomorrowConfirmed && tomorrowHasBlocks

    /**
     * Whether the phase is owed yet, as opposed to merely possible.
     *
     * Closing the day early stays allowed. What changes at the planning time is
     * how insistent the dashboard gets: before it, an unfinished phase is a quiet
     * offer, and from then on it is a reminder.
     *
     * Takes [now] rather than reading it at construction, so a screen that is
     * already open turns insistent at the minute itself rather than on the next
     * database change.
     */
    fun isOwed(now: LocalDateTime): Boolean = owedFrom == null || now >= owedFrom

    /** The one sentence the dashboard leads with. */
    val headline: String
        get() = when {
            isComplete -> "Alles erledigt."
            !todaySettled && tomorrowConfirmed -> "Heute ist noch nicht abgerechnet."
            todaySettled && tomorrowReadyToConfirm -> "Morgen ist geplant, aber noch nicht bestätigt."
            todaySettled -> "Morgen ist noch nicht geplant."
            tomorrowReadyToConfirm -> "Morgen ist vorgeplant — heute fehlt noch der Abschluss."
            else -> "Tagesabschluss steht aus."
        }
}
